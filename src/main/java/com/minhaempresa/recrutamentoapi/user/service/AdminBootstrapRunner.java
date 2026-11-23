package com.minhaempresa.recrutamentoapi.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates (or re-invites) the first administrator on startup. See {@link BootstrapAdminProperties}. */
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserService userService;
    private final BootstrapAdminProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        userService.bootstrapAdmin(properties);
    }
}
