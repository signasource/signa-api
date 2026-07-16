package com.signasource.signa_api.users.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.service.FriendshipService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class FriendshipControllerTest {

    private MockMvc mockMvc;

    @Mock private FriendshipService friendshipService;

    @InjectMocks private FriendshipController friendshipController;

    private CustomUserDetails mockUserDetails;
    private UUID currentUserId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        currentUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        User mockUser = new User();
        mockUser.setId(currentUserId);
        mockUserDetails = new CustomUserDetails(mockUser);

        mockMvc =
                MockMvcBuilders.standaloneSetup(friendshipController)
                        .setCustomArgumentResolvers(
                                new HandlerMethodArgumentResolver() {
                                    @Override
                                    public boolean supportsParameter(MethodParameter parameter) {
                                        return parameter
                                                .getParameterType()
                                                .isAssignableFrom(CustomUserDetails.class);
                                    }

                                    @Override
                                    public Object resolveArgument(
                                            MethodParameter parameter,
                                            ModelAndViewContainer mavContainer,
                                            NativeWebRequest webRequest,
                                            WebDataBinderFactory binderFactory) {
                                        return mockUserDetails;
                                    }
                                })
                        .build();
    }

    @Test
    void sendFriendRequest_ReturnsCreated() throws Exception {
        doNothing().when(friendshipService).sendFriendRequest(currentUserId, otherUserId);

        mockMvc.perform(post("/api/v1/friendships/request/{addresseeId}", otherUserId))
                .andExpect(status().isCreated());

        verify(friendshipService).sendFriendRequest(currentUserId, otherUserId);
    }

    @Test
    void acceptFriendRequest_ReturnsOk() throws Exception {
        doNothing().when(friendshipService).acceptFriendRequest(otherUserId, currentUserId);

        mockMvc.perform(put("/api/v1/friendships/accept/{requesterId}", otherUserId))
                .andExpect(status().isOk());

        verify(friendshipService).acceptFriendRequest(otherUserId, currentUserId);
    }
}
