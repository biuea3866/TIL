# [Spring] @Valid vs @Validated

- **Tags**: #Spring #SpringBoot #Validation #Valid #Validated

---

### 무엇을 배웠는가?
* Spring에서 데이터 유효성 검증에 사용되는 `@Valid`와 `@Validated` 어노테이션의 근본적인 차이점을 학습합니다.
* `@Valid`는 Java 표준 스펙(JSR-380)으로 객체 검증의 기본이며, `@Validated`는 Spring에서 제공하는 어노테이션으로 **객체 검증**, **유효성 검증 그룹** 지정 등의 기능을 제공합니다.

---

### 왜 중요하고, 어떤 맥락인가?
단순한 객체의 유효성 검증은 `@Valid`로 충분합니다.  
하지만 Json으로 파싱되지 않는 url parameter 등에 대한 유효성 검증 그리고 검증에 대한 순서 등이 필요한 순간이 오고, 이 문제를 해결해줍니다.

---

### 상세 내용
#### 1. `@Valid`: Java 표준 유효성 검증

`@Valid`는 Java의 Bean Validation 표준 스펙의 일부로, 객체의 유효성을 검증하는 지시자입니다.

* **동작 원리**: 
  * 1. Spring MVC에서는 `RequestMappingHandlerAdapter`가 `ArgumentResolver`를 호출하여 파라미터를 처리합니다.
  * 2. `@RequestBody`나 `@ModelAttribute`와 같이 객체로 변환되는 파라미터들은 각각의 `ArgumentResolver`가 `@Valid`를 인지하고 `WebDataBinder`를 통해 유효성 검증을 수행합니다.
* **사용 범위**: 주로 Controller 계층에서 `@RequestBody`나 `@ModelAttribute`가 붙은 객체를 대상으로 사용됩니다.
* **한계**:
    * `@RequestParam`, `@PathVariable` 등 ArgumentResolve가 인지하지 못하는 어노테이션은 사용할 수 없습니다.
    * **유효성 검증 그룹(groups) 기능을 지원하지 않습니다.** DTO 필드에 `groups` 속성을 지정하는 순간, `@Valid`는 해당 필드의 유효성 검사를 수행하지 않습니다.

#### 2. `@Validated`: 스프링이 제공하는 강력한 유효성 검증
`@Validated`는 Spring Framework가 자체적으로 제공하는 어노테이션으로, `@Valid`의 기능을 포함하며 더 많은 기능을 제공합니다.

* **동작 원리**: AOP 기반으로 동작합니다. 
  * 1. `@Validated`가 붙은 클래스는 Spring이 프록시 객체를 생성하고,
  * 2. `MethodValidationInterceptor`를 통해 메서드 호출 전에 파라미터에 대한 유효성 검사를 먼저 수행합니다.
* **주요 특징**:
    * **유효성 검증 그룹 기능을 지원합니다.** 이를 통해 복잡하고 순서가 중요한 검증 로직을 구현할 수 있습니다.
    * Controller 뿐만 아니라 Service 등 **모든 Spring Bean**에서 사용할 수 있습니다.
    * 클래스 레벨에 `@Validated`를 붙이면 `@RequestParam`, `@PathVariable` 등 단순 타입 파라미터에 대한 유효성 검증도 가능해집니다.
    * 그래서 사실은 `@RequestParam`, `@PathVariable`등을 타겟하는 것이 아닌 `ConstraintValidator`를 상속하거나, `@Constraint`가 붙어있는 유효성 검증 어노테이션을 타겟하여 검증을 수행합니다.

#### 3. 핵심 기능 비교: 유효성 검증 그룹 (Validation Groups)
DTO의 이메일 필드에 대해 **1. 패턴 검증 → 2. 길이 검증** 순서로 진행하고 싶다고 가정해 봅시다.

* **`@Valid` 사용 시 문제점**
    * DTO 필드에 `groups`를 지정하면, `@Valid`는 어떤 그룹을 검증해야 할지 몰라 **유효성 검사를 아예 수행하지 않습니다.**
    * 이는 내부적으로 `@Valid`가 `validationHints`라는 빈 배열을 반환하기 때문이며, 검증 그룹 정보가 없으므로 검증 로직이 실행되지 않습니다.

> @Valid와 group의 조합으로 유효성 검증이 동작하지 않는 이유
1. `@Valid`가 붙어있으면 Object[0]를 반환
```java
public abstract class ValidationAnnotationUtils {

    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
        
    ...
    
    @Nullable
    public static Object[] determineValidationHints(Annotation ann) {
        Class<? extends Annotation> annotationType = ann.annotationType();
        String annotationName = annotationType.getName();
        if ("jakarta.validation.Valid".equals(annotationName)) { // 빈 값 반환
            return EMPTY_OBJECT_ARRAY;
        }
        Validated validatedAnn = AnnotationUtils.getAnnotation(ann, Validated.class);
        if (validatedAnn != null) {
            Object hints = validatedAnn.value();
            return convertValidationHints(hints);
        }
        if (annotationType.getSimpleName().startsWith("Valid")) {
            Object hints = AnnotationUtils.getValue(ann);
            return convertValidationHints(hints);
        }
        return null;
    }
```

2. validationHints의 사이즈가 0이므로 asValidationGroups 메서드는 빈 리스트를 반환한다.
```java
@Override
public void validate(Object target, Errors errors, Object... validationHints) {
    if (this.targetValidator != null) {
        processConstraintViolations(
                this.targetValidator.validate(target, asValidationGroups(validationHints)), errors);
    }
}
```

3. validatae 메서드 동작 시 validationOrder를 순회하며 유효성 검증을 수행하나, 빈 리스트이므로 유효섬 검사가 수행되지 않는다.
```java
private <T, U> Set<ConstraintViolation<T>> validateInContext(BaseBeanValidationContext<T> validationContext, BeanValueContext<U, Object> valueContext,
			ValidationOrder validationOrder) {
    ...
    Iterator<Group> groupIterator = validationOrder.getGroupIterator();
    while ( groupIterator.hasNext() ) {
        Group group = groupIterator.next();
        valueContext.setCurrentGroup( group.getDefiningClass() );
        validateConstraintsForCurrentGroup( validationContext, valueContext );
        if ( shouldFailFast( validationContext ) ) {
            return validationContext.getFailingConstraints();
        }
    }
    ...
    return validationContext.getFailingConstraints();
}
```

* **`@Validated`를 사용한 해결책**
    * 컨트롤러 메서드의 파라미터에 `@Validated(그룹순서.class)`를 명시하면, 정의된 순서대로 유효성 검증이 정확하게 동작합니다.
    * 아래 예시처럼 첫 번째 그룹(패턴)에서 실패하면 두 번째 그룹(길이)은 검증하지 않으므로, 의도한 대로 동작하고 정확한 에러 메시지를 반환하게 됩니다.

---
### 핵심
* 단순 DTO 객체의 유효성 검증이 필요하고 순서가 중요하지 않다면 표준인 **@Valid**를 써도 무방합니다.
* 하지만 필드 간 검증 순서, `@RequestParam` 등과 같은 파라미터를 검증, Service 계층에서의 유효성 검증이 필요하다면 **@Validated**를 사용해야 합니다.