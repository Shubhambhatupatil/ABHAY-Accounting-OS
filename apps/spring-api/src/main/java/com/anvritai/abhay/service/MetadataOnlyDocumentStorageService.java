package com.anvritai.abhay.service;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MetadataOnlyDocumentStorageService implements DocumentStorageService {
    @Override
    public String reserveStorageKey(UUID companyId, UUID documentId, int version, String originalFileName) {
        int dot = originalFileName.lastIndexOf('.');
        String extension = dot < 0 ? "bin" : originalFileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return "companies/" + companyId + "/documents/" + documentId + "/v" + version + "." + extension;
    }
}
