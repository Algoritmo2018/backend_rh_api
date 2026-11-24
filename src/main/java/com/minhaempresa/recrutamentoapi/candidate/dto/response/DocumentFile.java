package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import org.springframework.core.io.Resource;

/**
 * A document file ready to be sent (download endpoint). Never contains the storage key.
 *
 * @param fileName safe name: original base name with the extension of the detected type
 * @param inline open in the browser (PDF and images only); otherwise saved as a file
 */
public record DocumentFile(Resource content, String contentType, String fileName, long sizeBytes, boolean inline) {}
