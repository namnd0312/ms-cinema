package com.namnd.cinema.model;


import lombok.Data;

import jakarta.persistence.*;
import java.util.Date;
import java.util.Set;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String password;

    private String fullName;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private boolean active = false;

    @Column(nullable = false)
    private int failedAttempts = 0;

    @Column(name = "lock_time")
    private Date lockTime;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = {@JoinColumn(name = "user_id")},
    inverseJoinColumns = {@JoinColumn(name = "role_id")})
    private Set<Role> roles;

}
