# 📄 [API 명세서] GEO 분석 리포트 상세 조회

GEMINI 자동 작성

## 1. 기본 정보

- **설명:** 백그라운드에서 분석이 완료된 특정 주문(Order)의 GEO 점수 및 상세 결과를 조회합니다.
- **Endpoint:** `/api/v1/geo/report/{orderId}`
- **Method:** `GET`
- **Content-Type:** `application/json`

## 2. Request (요청)

### Path Variable (경로 변수)

| **이름** | **타입** | **필수 여부** | **설명** |
| --- | --- | --- | --- |
| `orderId` | `Number` | **O** | 대시보드 목록에서 선택한 리포트의 고유 ID |
|  |  |  |  |

### Headers

| **Key** | **Value** | **필수 여부** | **설명** |
| --- | --- | --- | --- |
| `Authorization` | `Bearer {JWT_토큰}` | **O** | 로그인 시 발급받은 액세스 토큰 |

---

## 3. Response (응답) - 성공 (HTTP 200 OK)

프론트엔드에서 그래프나 차트를 그릴 때 사용할 `aiResult` 객체가 포함되어 반환됩니다.

JSON

```json
{
  "orderId": 5,
  "targetUrl": "https://www.apple.com/kr/",
  "jobStatus": "SUCCESS",
  "aiResult": {
    "status": "success",
    "result": "총점 95점! SEO 마크업이 매우 훌륭합니다.\n[개선점] 메타 태그의 길이를 조금 줄이세요." 
  },
  "createdAt": "2026-03-26T15:30:00"
}
```

---

## 💻 4. 프론트엔드(HTML+JS) 연동 가이드 예제

프론트엔드 개발자분이 입력창(Input)에서 `orderId`를 받아서 바로 호출해 볼 수 있는 바닐라 자바스크립트(Vanilla JS) 예제 코드입니다.

### HTML 구조

HTML

```html
<div>
    <input type="number" id="orderIdInput" placeholder="조회할 주문 번호(예: 5) 입력">
    <button onclick="fetchGeoReport()">결과 조회하기</button>
</div>

<div id="resultContainer" style="margin-top: 20px; padding: 10px; border: 1px solid #ccc;">
    결과가 이곳에 표시됩니다.
</div>
```

### JavaScript 호출 로직

JavaScript

```jsx
async function fetchGeoReport() {
    // 1. 입력창에서 orderId 가져오기
    const orderId = document.getElementById('orderIdInput').value;
    
    // (주의) 실제 환경에서는 로그인 시 로컬 스토리지 등에 저장해둔 토큰을 꺼내와야 합니다.
    const jwtToken = localStorage.getItem("ACCESS_TOKEN"); 

    if (!orderId) {
        alert("주문 번호를 입력해주세요!");
        return;
    }

    try {
        // 2. 백엔드 API 호출
        const response = await fetch(`/api/v1/geo/report/${orderId}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${jwtToken}` // 수문장 통과를 위한 토큰 셋업
            }
        });

        // 3. 에러 처리 (권한 없음, 없는 주문 등)
        if (!response.ok) {
            throw new Error(`서버 에러 발생: ${response.status}`);
        }

        // 4. 데이터 파싱 및 화면에 그리기
        const data = await response.json();
        
        const resultContainer = document.getElementById('resultContainer');
        resultContainer.innerHTML = `
            <h3>✅ 조회 성공</h3>
            <p><strong>타겟 URL:</strong> ${data.targetUrl}</p>
            <p><strong>분석 상태:</strong> ${data.jobStatus}</p>
            <p><strong>AI 피드백:</strong> <br> ${data.aiResult.result.replace(/\n/g, '<br>')}</p>
            <p><small>요청 일시: ${data.createdAt}</small></p>
        `;

    } catch (error) {
        console.error("API 호출 실패:", error);
        alert(error.message);
    }
}
```