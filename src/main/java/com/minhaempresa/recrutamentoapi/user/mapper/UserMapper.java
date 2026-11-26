package com.minhaempresa.recrutamentoapi.user.mapper;

import com.minhaempresa.recrutamentoapi.user.dto.response.UserResponse;
import com.minhaempresa.recrutamentoapi.user.dto.response.UserSummaryResponse;
import com.minhaempresa.recrutamentoapi.user.entity.User;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserCredentials;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(imports = UserRole.class)
public interface UserMapper {

    @Mapping(target = "emailVerified", expression = "java(user.getEmailVerifiedAt() != null)")
    // Staff and client users have no password until they accept the invitation; candidates always set one
    @Mapping(target = "invitationPending", expression = "java(!user.hasPassword() && user.getRole() != UserRole.CANDIDATE)")
    UserResponse toResponse(User user);

    UserSummaryResponse toSummary(User user);

    UserCredentials toCredentials(User user);
}
