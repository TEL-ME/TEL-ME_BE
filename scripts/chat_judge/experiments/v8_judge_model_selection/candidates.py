"""채점 후보 모델의 리전, 모델 ID, 공개 단가. 호출은 하지 않는다."""

# USD / 100만 토큰 (입력, 출력). 출처: AWS Bedrock 요금표, Asia Pacific (Tokyo) 표기. 실제 청구액과 다를 수 있다
CANDIDATES = {
    "mistral": {"region": "ap-northeast-1", "modelId": "mistral.mistral-large-3-675b-instruct",
                "price": (0.61, 1.82)},
    "deepseek": {"region": "ap-northeast-1", "modelId": "deepseek.v3.2", "price": (0.74, 2.22)},
}

# 기존 V7 결과의 단가. Qwen은 Sydney 표기값, Sonnet은 Anthropic 공개 가격
BASELINE_PRICES = {"qwen": (0.2266, 0.9064), "sonnet-4-6": (3.0, 15.0), "gpt-oss-120b": (0.1545, 0.618)}


def cost(prompt_tokens, output_tokens, price):
    return prompt_tokens / 1e6 * price[0] + output_tokens / 1e6 * price[1]
