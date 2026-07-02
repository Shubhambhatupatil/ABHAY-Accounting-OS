package com.anvritai.abhay.security;

import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AbhayUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AbhayUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.isActive());
    }
}
