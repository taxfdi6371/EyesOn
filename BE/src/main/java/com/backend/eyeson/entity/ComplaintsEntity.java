package com.backend.eyeson.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name="t_complaints", schema = "eyeson")
public class ComplaintsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comp_seq")
    private long compSeq;

<<<<<<< HEAD
    @ManyToOne
    @JoinColumn(name = "blind_seq")
    private UserEntity blindUser;

    @ManyToOne
    @JoinColumn(name = "angel_seq")
=======
    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "blind_seq", insertable = false, updatable = false)
    private UserEntity blindUser;

    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "angel_seq", insertable = false, updatable = false)
>>>>>>> 377b0aec5de2a347452e7f6a25620f1418a6c526
    private UserEntity angelUser;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "comp_state")
    private CompStateEnum compState;

    @Basic
    @Column(name = "comp_return")
    private String compReturn;

    @Basic
    @Column(name = "comp_address", length = 256)
    private String compAddress;

    @Basic
    @Column(name = "comp_image", length = 256)
    private String compImage;

    @Basic
    @Column(name = "comp_title", length = 100)
    private String compTitle;

    @Basic
    @Column(name = "comp_content")
    private String compContent;

    @Basic
    @CreatedDate
    @Column(name = "comp_regtime")
    private LocalDateTime compRegtime;

    @Basic
    @Column(name = "comp_result_content")
    private String compResultContent;
}
