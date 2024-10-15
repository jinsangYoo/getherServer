# 개요

- 개인 git을 회사 테스트용도로 사용하면서 git의 이름과 용도가 갑자기 기억이 나지 않을때를 대비해 기록

# git

- react.js with next.js 웹사이트
  - https://github.com/jinsangYoo/myNext
    - 프로젝트 폴더에서
      - `npm run dev`
      - 별도로 배포하지 않음
        - https가 없어 react.js 웹사이트에서 푸시테스트 대체
      - [ACP-A3/324 QA react with nextjs 샘플 웹사이트 사용법](dooray://1387695619080878080/tasks/3540721510686713753 'closed')
- react.js 웹사이트
  - https://github.com/jinsangYoo/myReact
    - view-with-react 에서
      1. `npm run build`
      2. view-with-react/build 폴더 내부에서 아래 파일들을 복사
         - static 폴더
         - asset-manifest.json
         - index.html
         - 만약, firebase-messaging-sw.js 가 변경된 경우 포함
      3. 상위 폴더의 /public 폴더 내부에서
         - 2번에서 복사한 동일 파일, 폴더를 삭제 후 붙여넣기
      4. `firebase login` (이미 되어 있는 경우 패스)
         - demojinsang@gmail.com
           - [ACP-A3/307 QA react 샘플 웹사이트 사용법](dooray://1387695619080878080/tasks/3423886890274746135 'closed')
      5. `firebase deploy`
         - 오류
           - `Error: Assertion failed: resolving hosting target of a site with no site name or target name. This should have caused an error earlier` 발생시
           - `firebase logout` 후 `firebase login` 으로 해결됨
      6. https://rnfornhndata.web.app/
- react SDK
  - https://github.com/jinsangYoo/slimer-react
    - 빌드
      - `npm run rollup` 으로 빌드 진행
      - 개발환경인 경우
        - `npm pack` 으로 tgz 파일 만들어 path로 연동테스트
          - [@jinsang/123 local rn-sdk install 방법](dooray://1387695619080878080/tasks/3249375898678480856 'registered')
      - 배포하는 경우
        - SDK 버전 올려야 하는것 유의
        - `npm publish`
    - [@jinsang/127 react, react-native SDK 개발시 알게된 것들](dooray://1387695619080878080/tasks/3386291409628719499 'registered')
- RN iOS, AOS 모바일 앱 프로젝트
  - https://github.com/jinsangYoo/rnForNHNDATA
    - 보통 local 로 설치
      1. package.json 파일에 `ace.sdk.react-native` 항목 제거 후 저장
      2. `npm install`
      3. `"ace.sdk.react-native": "file:../reactSlimer.git/reactslimer-2.9.0.tgz",` 추가 후 저장
      4. `npm install`
      5. `cd android && ./gradlew clean && ./gradlew :app  :bundleRelease` 변경사항 바로 반영을 위해
      6. 비슷하게 iOS 는 `pod install` 함
    - 배포
      - 각각의 프로젝트를 IDE 도구로 직접 열어 배포
        - AOS의 경우 내 개인 안드로이드 개발자 계정
        - iOS의 경우 회사 개발자 계정
        - 푸시
          - firebase 한곳에 둘다 보냄
- RN SDK
  - https://github.com/jinsangYoo/reactSlimer
    - 빌드
      - `npm run lint` 로 lint 오류 확인
      - 개발환경인 경우
        - `npm pack` 으로 tgz 파일 만들어 path로 연동테스트
          - [@jinsang/123 local rn-sdk install 방법](dooray://1387695619080878080/tasks/3249375898678480856 'registered')
      - 배포하는 경우
        - SDK 버전 올려야 하는것 유의
          - SDK 버전을 문자열 상수로 유지중 react 처럼 바꿔야 함
        - `npm publish`
