package ru.workbit.security.model;

import java.util.Collection;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {
    private final UUID id;
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;

    @Override
    public @NotNull String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return null;
    }
}