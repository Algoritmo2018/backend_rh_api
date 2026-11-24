package com.minhaempresa.recrutamentoapi.notification.mapper;

import com.minhaempresa.recrutamentoapi.notification.dto.response.EmailAccountResponse;
import com.minhaempresa.recrutamentoapi.notification.dto.response.NotificationResponse;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailAccount;
import com.minhaempresa.recrutamentoapi.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);

    /** Never the password: only whether one is stored. */
    @Mapping(target = "hasPassword", expression = "java(account.getPasswordEncrypted() != null)")
    EmailAccountResponse toResponse(EmailAccount account);
}
