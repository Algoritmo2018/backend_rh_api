package com.minhaempresa.recrutamentoapi.shared.storage;

/**
 * Malware scan of an uploaded file before it is stored (decision D6-6). Until the deployment stage
 * (ClamAV) the only implementation accepts everything; the defenses in place are the type detection by
 * content, the refusal of macros, {@code Content-Disposition: attachment} and {@code CSP: sandbox}.
 */
public interface FileScanner {

    /** @throws com.minhaempresa.recrutamentoapi.shared.exception.BusinessException if the file is infected */
    void scan(byte[] content);
}
