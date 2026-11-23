package com.minhaempresa.recrutamentoapi.shared.config;

import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUserArgumentResolver;
import com.minhaempresa.recrutamentoapi.shared.web.WireEnumConverterFactory;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Spring MVC customizations. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Query and path parameters use the same enum values as the JSON ({@code ?province=Icolo e Bengo}). */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new WireEnumConverterFactory());
    }

    /** Controllers can declare an {@code AuthenticatedUser} parameter. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticatedUserArgumentResolver());
    }
}
