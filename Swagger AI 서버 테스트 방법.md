# Swagger AI 서버 테스트 방법!

## 1. GeoScrapingServiceTest.java를 IDE 창에 열기
backend\src\test\java\com\malgeum\geo\serivce\ 에 있는 GeoScrapingServiceTest.java를 IDE에서 열어줍니다.

## 2. 실행버튼을 통해 테스트 파일 실행하고, 결과 출력물을 **디버그 콘솔(Debug Console)**의 아래 부분에서 복사!

## 3. swagger 페이지의 입력창에 그대로 붙혀넣고, execute 하기
```
{
  "url": "string",
  "html_text": "string",
  "json_ld": "string"
}
```
위의 형식에 맞아야합니다! 
**주의 사항!!!** AI모델에 토큰 한도가 존재합니다. 따라서, 너무 긴 html 본문을 입력하면 원하는 결과물이 안나올 수 있으니 일부 지워서 짧게 만드세요! 