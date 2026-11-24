package com.minhaempresa.recrutamentoapi.shared.storage;

import org.springframework.stereotype.Component;

/**
 * Placeholder until an antivirus (ClamAV) is deployed: accepts every file. Replace this bean (not add
 * a second one) when the real scanner arrives.
 */
@Component
public class NoOpFileScanner implements FileScanner {

    @Override
    public void scan(byte[] content) {
        // No antivirus yet (decision D6-6)
    }
}
