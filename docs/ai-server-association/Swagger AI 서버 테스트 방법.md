# Swagger AI 서버 테스트 방법!

## 1. GeoScrapingServiceTest.java를 IDE 창에 열기
backend\src\test\java\com\malgeum\geo\serivce\ 에 있는 GeoScrapingServiceTest.java를 IDE에서 열어줍니다.

<img width="1092" height="674" alt="image" src="https://github.com/user-attachments/assets/a11b2975-fca5-4bab-bb16-3805aa3baf11" />
위 사진에서 url 변수 값을 조정해주어 원하는 url을 크롤링할 수 있습니다!

## 2. 실행버튼을 통해 테스트 파일 실행하고, 결과 출력물을 **디버그 콘솔(Debug Console)**의 아래 부분에서 복사!
<img width="1260" height="528" alt="image" src="https://github.com/user-attachments/assets/409e87ec-d67e-4c42-b4ee-2472930996b4" />

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

## 4. 제대로 잘 했다면, 바로 아래에 Response 200 이 존재하고, 원하는 GEO 출력값 확인가능!
<img width="1321" height="818" alt="image" src="https://github.com/user-attachments/assets/9ba96f70-a134-4814-b925-7751037af7ed" />
