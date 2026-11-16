package fr.ece.autoloc.config;

import fr.ece.autoloc.repository.ClientRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * Two sources of accounts:
     * - agency staff, in memory (local classroom practice only): admin (ADMIN), student (USER, read only);
     * - customers, loaded from MySQL: login = email, BCrypt password stored in client.mot_de_passe (CLIENT).
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder, ClientRepository clientRepository) {
        UserDetails student = User.withUsername("student")
                .password(encoder.encode("student123"))
                .roles("USER")
                .build();
        UserDetails admin = User.withUsername("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN")
                .build();
        InMemoryUserDetailsManager staff = new InMemoryUserDetailsManager(student, admin);

        return username -> {
            if (staff.userExists(username)) {
                return staff.loadUserByUsername(username);
            }
            return clientRepository.findByEmailIgnoreCase(username)
                    .filter(client -> client.getMotDePasse() != null)
                    .map(client -> User.withUsername(client.getEmail())
                            .password(client.getMotDePasse())
                            .roles("CLIENT")
                            .build())
                    .orElseThrow(() -> new UsernameNotFoundException("Compte inconnu"));
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Rules are evaluated in order: the most specific paths must come first
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        // Website static files (the data itself stays protected by /api/**)
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/favicon.svg").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        // Customer space: sign-up is open, everything else requires the CLIENT role
                        .requestMatchers(HttpMethod.POST, "/api/client/inscription").permitAll()
                        .requestMatchers("/api/client/**").hasRole("CLIENT")
                        // Reference values (categories, payment modes...) are public; session info needs a login
                        .requestMatchers(HttpMethod.GET, "/api/enums").permitAll()
                        .requestMatchers("/api/me").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Console: everyone reads, only ADMIN creates, modifies or deletes
                        .requestMatchers(HttpMethod.GET, "/api/console/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/console/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                // Stateless API: credentials are sent explicitly in every request (no session cookie),
                // so the CSRF token is not needed on /api/**
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));
        return http.build();
    }
}
