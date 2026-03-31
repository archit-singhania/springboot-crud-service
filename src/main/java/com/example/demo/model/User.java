package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "USER";

    @Column(nullable = false)
    private boolean isDeleted = false;

    private String oktaId;

    private String userType;

    @Column(columnDefinition = "jsonb")
    private String permissions; 

    @Column(columnDefinition = "jsonb")
    private String resources;    

    @Column(columnDefinition = "jsonb")
    private String metadata;   
}
