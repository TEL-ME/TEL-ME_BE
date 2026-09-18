package com.telme.faq.dto.req;

import java.util.List;

public record EmbedRequest(String model, List<String> input) {
}
