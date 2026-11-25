package com.minhaempresa.recrutamentoapi.application.dto.response;

/**
 * Answer of the public application form: always the same when the job is visible, whether a new application was
 * stored or the email already had one for this job (stage 8, adjustment 2), so the endpoint reveals nothing.
 */
public record ApplicationReceivedResponse(String status) {

    public static final ApplicationReceivedResponse RECEIVED = new ApplicationReceivedResponse("received");
}
