package com.anvritai.abhay.service;

import java.util.UUID;

public interface DocumentStorageService {
    String reserveStorageKey(UUID companyId, UUID documentId, int version, String originalFileName);
}
