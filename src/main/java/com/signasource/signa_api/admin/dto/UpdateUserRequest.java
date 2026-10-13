package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.users.entity.Role;

public record UpdateUserRequest(Role role, Boolean enabled) {}
