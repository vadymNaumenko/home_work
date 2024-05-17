package jpa_repository.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@NamedEntityGraph(
        name = "User.company",
        attributeNodes = @NamedAttributeNode("company"))
@Data
@EqualsAndHashCode(of = "username")
@ToString(exclude = "company")
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    private LocalDate birthDate;

    private String firstname;

    private String lastname;

    @Enumerated(EnumType.STRING)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;
}
