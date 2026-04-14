# URI vs URL vs URN

## URI (Uniform Resource Identifier)

리소스를 식별하기 위한 일반적인 메커니즘으로 URL (Uniform Resource Locator)과 URN (Uniform Resource Name)을 포함하는 상위 개념

* Uniform: 리소스를 식별하는 통일된 방식
* Resource: 자원 URI로 식별할 수 있는 모든 것
* Identifier: 다른 항목과 구분하는 정보

## URL (Uniform Resource Locator)

파일 식별자로 네트워크 상에 자원이 어디있는지 위치를 알려준다

* scheme: 통신 방식
* hosts: 웹 서버의 이름이나 도메인, IP주소
* url-path: 웹 서버에서 지정한 루트 디렉토리부터 시작하여 파일이 위치한 경로를 나타낸다
* query: 웹 서버에 보내는 옵셔널한 질의

```
https://localhost:8080/workspaces/1?name=테스트
scheme |     hosts    | url-path   | query
```

## URN (Uniform Resource Name)

리소스에 이름을 부여하여 영구적이고 위치에 독립적인 리소스 식별자

`workspaces:1` 과 같이 콜론을 이용하여 이름을 붙임

```
urn:isbn:0451450523          (ISBN 도서 식별)
urn:ietf:rfc:2648            (IETF RFC 문서)
```

* 리소스가 이동하거나 삭제되더라도 이름은 영구적으로 유효
* 실무에서는 URL이 압도적으로 사용되며, URN은 표준 문서/도서 식별 등 제한적 사용

---

## 관계 정리

```
              URI (Uniform Resource Identifier)
             ┌─────────────┴─────────────┐
            URL                         URN
     (위치로 식별)                  (이름으로 식별)
```

* 모든 URL은 URI이다 (O)
* 모든 URI는 URL이다 (X) — URN도 URI의 하위 개념
* REST API 설계에서는 "URI"라는 용어를 사용하는 것이 정확하다
