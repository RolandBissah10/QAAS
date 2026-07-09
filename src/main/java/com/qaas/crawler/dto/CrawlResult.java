package com.qaas.crawler.dto;

import java.util.List;

public record CrawlResult(List<PageInfo> pages, String storageStateJson) {}