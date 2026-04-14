#!/bin/bash
# Confluence 이미지 일괄 다운로드 스크립트
# 사용법: ./download_confluence_images.sh <이메일> <API토큰>
# API 토큰 생성: https://id.atlassian.com/manage-profile/security/api-tokens

EMAIL=$1
TOKEN=$2
BASE="https://doodlin.atlassian.net/wiki/download/attachments"
TIL="/Users/biuea/TIL"

if [ -z "$EMAIL" ] || [ -z "$TOKEN" ]; then
  echo "사용법: ./download_confluence_images.sh <이메일> <API토큰>"
  echo "API 토큰 생성: https://id.atlassian.com/manage-profile/security/api-tokens"
  exit 1
fi

AUTH=$(echo -n "$EMAIL:$TOKEN" | base64)

download() {
  local page_id=$1
  local filename=$2
  local dest=$3
  local url="$BASE/$page_id/$filename"
  echo "다운로드: $dest"
  curl -s -L -o "$dest" -H "Authorization: Basic $AUTH" "$url"
  if [ $? -eq 0 ] && [ -s "$dest" ]; then
    echo "  ✓ 성공"
  else
    echo "  ✗ 실패"
  fi
}

# 디렉토리 생성
mkdir -p "$TIL/images/mysql" "$TIL/images/java" "$TIL/images/jvm"

echo "=== ERD 표기법 (4장) ==="
download 1273036828 "image-20250907-113106.png" "$TIL/images/mysql/erd_peter_chen.png"
download 1273036828 "image-20250907-114118.png" "$TIL/images/mysql/erd_ie_entity.png"
download 1273036828 "image-20250907-114136.png" "$TIL/images/mysql/erd_ie_relation.png"
download 1273036828 "image-20250907-114629.png" "$TIL/images/mysql/erd_ie_example.png"

echo ""
echo "=== Map (4장) ==="
download 1119289498 "스크린샷 2025-08-04 21.15.10.png" "$TIL/images/java/map_put_1.png"
download 1119289498 "스크린샷 2025-08-04 21.33.08.png" "$TIL/images/java/map_put_2_collision.png"
download 1119289498 "스크린샷 2025-08-04 21.43.58.png" "$TIL/images/java/map_get_1.png"
download 1119289498 "스크린샷 2025-08-04 21.44.25.png" "$TIL/images/java/map_get_2_collision.png"

echo ""
echo "=== 프로세스와 쓰레드 비용 (4장) ==="
download 1142423582 "스크린샷 2025-08-10 14.33.19.png" "$TIL/images/jvm/process_context_switch.png"
download 1142423582 "스크린샷 2025-08-10 14.44.59.png" "$TIL/images/jvm/tlb_flush_problem.png"
download 1142423582 "스크린샷 2025-08-10 15.00.46.png" "$TIL/images/jvm/tlb_asid.png"
download 1142423582 "스크린샷 2025-08-10 15.01.35.png" "$TIL/images/jvm/thread_context_switch.png"

echo ""
echo "=== 완료 ==="
echo "총 12장 이미지 다운로드 시도 완료"
echo ""
echo "다운로드 후 마크다운에 이미지 참조 추가하려면:"
echo "  ./scripts/update_image_refs.sh"
