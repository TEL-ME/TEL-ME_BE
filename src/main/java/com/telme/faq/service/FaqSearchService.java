package com.telme.faq.service;

import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;

public interface FaqSearchService {

    List<FaqSearchResponse> search(FaqSearchRequest request);
}
