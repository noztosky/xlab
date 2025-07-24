#!/bin/bash

# 버전 업데이트 스크립트
# 사용법: ./update_version.sh

GRADLE_FILE="app/build.gradle.kts"

if [ ! -f "$GRADLE_FILE" ]; then
    echo "❌ $GRADLE_FILE 파일을 찾을 수 없습니다."
    exit 1
fi

# 현재 versionCode 읽기
CURRENT_VERSION=$(grep "versionCode = " "$GRADLE_FILE" | grep -o '[0-9]\+')

if [ -z "$CURRENT_VERSION" ]; then
    echo "❌ 현재 버전 코드를 찾을 수 없습니다."
    exit 1
fi

# 새 버전 계산
NEW_VERSION=$((CURRENT_VERSION + 1))

# versionCode 업데이트
sed -i "s/versionCode = $CURRENT_VERSION/versionCode = $NEW_VERSION/g" "$GRADLE_FILE"

# versionName도 업데이트 (1.x 형태로)
NEW_VERSION_NAME="1.$NEW_VERSION"
sed -i "s/versionName = \"[^\"]*\"/versionName = \"$NEW_VERSION_NAME\"/g" "$GRADLE_FILE"

echo "✅ 버전 업데이트 완료!"
echo "   이전: v1.$CURRENT_VERSION (빌드 #$CURRENT_VERSION)"
echo "   현재: v$NEW_VERSION_NAME (빌드 #$NEW_VERSION)"
echo ""
echo "🔧 변경 사항:"
echo "   - versionCode: $CURRENT_VERSION → $NEW_VERSION"
echo "   - versionName: → $NEW_VERSION_NAME"
echo ""
echo "📱 이제 앱을 빌드하면 상단에 새 버전 번호가 표시됩니다!" 