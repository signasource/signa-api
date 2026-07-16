package com.signasource.signa_api.users.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    @Transactional
    public void sendFriendRequest(UUID requesterId, UUID addresseeId) {
        if (requesterId.equals(addresseeId)) {
            throw new InvalidInputException("No puedes enviarte una solicitud a ti mismo.");
        }

        User requester =
                userRepository
                        .findById(requesterId)
                        .orElseThrow(() -> new NotFoundException("Usuario origen no encontrado"));
        User addressee =
                userRepository
                        .findById(addresseeId)
                        .orElseThrow(() -> new NotFoundException("Usuario destino no encontrado"));

        if (friendshipRepository.friendshipExists(requester, addressee)) {
            throw new InvalidInputException("La relación o solicitud ya existe.");
        }

        Friendship friendship =
                Friendship.builder()
                        .requester(requester)
                        .addressee(addressee)
                        .status(FriendshipStatus.PENDING)
                        .build();

        friendshipRepository.save(friendship);
    }

    @Transactional
    public void acceptFriendRequest(UUID requesterId, UUID addresseeId) {
        User requester =
                userRepository
                        .findById(requesterId)
                        .orElseThrow(() -> new NotFoundException("Usuario origen no encontrado"));
        User addressee =
                userRepository
                        .findById(addresseeId)
                        .orElseThrow(() -> new NotFoundException("Usuario destino no encontrado"));

        Friendship friendship =
                friendshipRepository
                        .findByRequesterAndAddressee(requester, addressee)
                        .orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new InvalidInputException(
                    "Solo se pueden aceptar solicitudes que estén pendientes.");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
    }
}
