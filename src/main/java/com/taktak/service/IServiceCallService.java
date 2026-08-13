package com.taktak.service;

import com.taktak.controller.ServiceCallController.CreateServiceCallPayload;
import com.taktak.model.ServiceCall;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IServiceCallService {
    ServiceCall createServiceCall(String slug, CreateServiceCallPayload payload);
    List<ServiceCall> getActiveServiceCalls(String slug);
    Optional<ServiceCall> dismissServiceCall(UUID id);
}
