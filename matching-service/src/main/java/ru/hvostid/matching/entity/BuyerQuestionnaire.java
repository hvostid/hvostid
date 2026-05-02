package ru.hvostid.matching.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "buyer_questionnaire")
public class BuyerQuestionnaire {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "living_space", nullable = false)
    private LivingSpace livingSpace;

    @Column(name = "living_area", nullable = false)
    private Integer livingArea;

    @Column(name = "has_yard", nullable = false)
    private Boolean hasYard;

    @Column(name = "has_children", nullable = false)
    private Boolean hasChildren;

    @Column(name = "children_age_min")
    private Integer childrenAgeMin;

    @Column(name = "has_allergies", nullable = false)
    private Boolean hasAllergies;

    @Column(name = "allergy_details", length = 2000)
    private String allergyDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "pet_experience", nullable = false)
    private PetExperience petExperience;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", nullable = false)
    private ActivityLevel activityLevel;

    @Column(name = "monthly_budget", nullable = false)
    private Integer monthlyBudget;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_schedule", nullable = false)
    private WorkSchedule workSchedule;

    @Column(name = "ready_for_adaptation", nullable = false)
    private Boolean readyForAdaptation;

    @Column(name = "preferred_species")
    private String preferredSpecies;

    @Column(name = "preferred_breed")
    private String preferredBreed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BuyerQuestionnaire() {
    }

    public BuyerQuestionnaire(Long userId) {
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LivingSpace getLivingSpace() {
        return livingSpace;
    }

    public void setLivingSpace(LivingSpace livingSpace) {
        this.livingSpace = livingSpace;
    }

    public Integer getLivingArea() {
        return livingArea;
    }

    public void setLivingArea(Integer livingArea) {
        this.livingArea = livingArea;
    }

    public Boolean getHasYard() {
        return hasYard;
    }

    public void setHasYard(Boolean hasYard) {
        this.hasYard = hasYard;
    }

    public Boolean getHasChildren() {
        return hasChildren;
    }

    public void setHasChildren(Boolean hasChildren) {
        this.hasChildren = hasChildren;
    }

    public Integer getChildrenAgeMin() {
        return childrenAgeMin;
    }

    public void setChildrenAgeMin(Integer childrenAgeMin) {
        this.childrenAgeMin = childrenAgeMin;
    }

    public Boolean getHasAllergies() {
        return hasAllergies;
    }

    public void setHasAllergies(Boolean hasAllergies) {
        this.hasAllergies = hasAllergies;
    }

    public String getAllergyDetails() {
        return allergyDetails;
    }

    public void setAllergyDetails(String allergyDetails) {
        this.allergyDetails = allergyDetails;
    }

    public PetExperience getPetExperience() {
        return petExperience;
    }

    public void setPetExperience(PetExperience petExperience) {
        this.petExperience = petExperience;
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(ActivityLevel activityLevel) {
        this.activityLevel = activityLevel;
    }

    public Integer getMonthlyBudget() {
        return monthlyBudget;
    }

    public void setMonthlyBudget(Integer monthlyBudget) {
        this.monthlyBudget = monthlyBudget;
    }

    public WorkSchedule getWorkSchedule() {
        return workSchedule;
    }

    public void setWorkSchedule(WorkSchedule workSchedule) {
        this.workSchedule = workSchedule;
    }

    public Boolean getReadyForAdaptation() {
        return readyForAdaptation;
    }

    public void setReadyForAdaptation(Boolean readyForAdaptation) {
        this.readyForAdaptation = readyForAdaptation;
    }

    public String getPreferredSpecies() {
        return preferredSpecies;
    }

    public void setPreferredSpecies(String preferredSpecies) {
        this.preferredSpecies = preferredSpecies;
    }

    public String getPreferredBreed() {
        return preferredBreed;
    }

    public void setPreferredBreed(String preferredBreed) {
        this.preferredBreed = preferredBreed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
