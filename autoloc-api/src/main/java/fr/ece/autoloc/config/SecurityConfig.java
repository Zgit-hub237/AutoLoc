package fr.ece.autoloc.config;

import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.EmployeRepository;
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

/**
 * Roles of the application:
 * <ul>
 *   <li>ADMIN   - administrator (in memory): everything, including agencies and employees;</li>
 *   <li>MANAGER - employee with role MANAGER: whole console except agencies and employees (read only);</li>
 *   <li>AGENT   - employee with role AGENT: front desk, creates and updates clients, reservations,
 *                 contracts, payments and maintenances, never deletes;</li>
 *   <li>USER    - student (in memory, workshop 2): console in read only;</li>
 *   <li>CLIENT  - customer (database): customer space only.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private static final String[] PERSONNEL = {"USER", "AGENT", "MANAGER", "ADMIN"};

    /**
     * Three sources of accounts, looked up in this order:
     * 1. in memory: admin (ADMIN), student (USER) - local classroom practice only;
     * 2. employees in MySQL: login + BCrypt password, role AGENT or MANAGER from Employe.role;
     * 3. customers in MySQL: login = email, BCrypt password (CLIENT).
     * An employee or a customer without password has no access.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder, EmployeRepository employeRepository,
                                                 ClientRepository clientRepository) {
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
            var employe = employeRepository.findByLoginIgnoreCase(username)
                    .filter(e -> e.getMotDePasse() != null);
            if (employe.isPresent()) {
                return User.withUsername(employe.get().getLogin())
                        .password(employe.get().getMotDePasse())
                        .roles(employe.get().getRole().name())          // AGENT or MANAGER
                        .build();
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
                        // ---- Agency console
                        // every staff member can read
                        .requestMatchers(HttpMethod.GET, "/api/console/**").hasAnyRole(PERSONNEL)
                        // agencies and employees (accounts): administrator only
                        .requestMatchers("/api/console/agences/**", "/api/console/employes/**").hasRole("ADMIN")
                        // deleting: manager and administrator
                        .requestMatchers(HttpMethod.DELETE, "/api/console/**").hasAnyRole("MANAGER", "ADMIN")
                        // the fleet (vehicles, equipment): manager and administrator
                        .requestMatchers("/api/console/vehicules/**", "/api/console/equipements/**")
                        .hasAnyRole("MANAGER", "ADMIN")
                        // front desk (clients, reservations, contracts, payments, maintenances): agents too
                        .requestMatchers("/api/console/**").hasAnyRole("AGENT", "MANAGER", "ADMIN")
                        .requestMatchers("/api/**").hasAnyRole(PERSONNEL)
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                // Stateless API: credentials are sent explicitly in every request (no session cookie),
                // so the CSRF token is not needed on /api/**
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));
        return http.build();
    }
}
