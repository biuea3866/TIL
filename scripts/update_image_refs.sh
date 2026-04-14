#!/bin/bash
# 다운로드된 이미지를 마크다운에 참조 추가하는 스크립트
TIL="/Users/biuea/TIL"

add_image() {
  local file=$1
  local after_text=$2
  local img_ref=$3

  if grep -q "$img_ref" "$file" 2>/dev/null; then
    echo "  이미 존재: $img_ref"
    return
  fi

  # after_text 다음 줄에 이미지 참조 추가
  sed -i '' "/$(echo "$after_text" | sed 's/[\/&]/\\&/g')/a\\
\\
$img_ref\\
" "$file"
  echo "  추가: $img_ref → $file"
}

echo "=== ERD 표기법 ==="
F="$TIL/mysql/erd_notation.md"
add_image "$F" "## Peter-Chen 표기법" '![Peter-Chen 표기법](../images/mysql/erd_peter_chen.png)'
add_image "$F" "엔티티의 이름을 최상단에" '![정보 공학 표기법 - 엔티티](../images/mysql/erd_ie_entity.png)'
add_image "$F" "### 관계선 유형" '![관계선 유형](../images/mysql/erd_ie_relation.png)'
add_image "$F" "### 카디널리티" '![카디널리티 예시](../images/mysql/erd_ie_example.png)'

echo ""
echo "=== Map ==="
F="$TIL/java/map.md"
add_image "$F" "### PUT" '![HashMap PUT](../images/java/map_put_1.png)'
add_image "$F" "해시 충돌이 발생한다면" '![HashMap PUT - 해시 충돌](../images/java/map_put_2_collision.png)'
add_image "$F" "### GET" '![HashMap GET](../images/java/map_get_1.png)'
add_image "$F" "next를 순회한다" '![HashMap GET - 충돌 순회](../images/java/map_get_2_collision.png)'

echo ""
echo "=== 프로세스와 쓰레드 비용 ==="
F="$TIL/jvm/process_thread_cost.md"
add_image "$F" "### 프로세스 컨텍스트 스위칭" '![프로세스 컨텍스트 스위칭](../images/jvm/process_context_switch.png)'
add_image "$F" "다음과 같은 상황에서 문제가 발생" '![TLB 플러시 문제](../images/jvm/tlb_flush_problem.png)'
add_image "$F" "ASID.*구분자를 하나 더 두는" '![TLB ASID 구조](../images/jvm/tlb_asid.png)'
add_image "$F" "### 쓰레드 컨텍스트 스위칭" '![쓰레드 컨텍스트 스위칭](../images/jvm/thread_context_switch.png)'

echo ""
echo "=== 완료 ==="
