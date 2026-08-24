package com.replog.app.data.repository

import com.replog.app.data.local.ActivityDao
import com.replog.app.data.local.ActivityEventEntity
import com.replog.app.data.local.GoalDao
import com.replog.app.data.local.GoalEntity
import com.replog.app.data.local.UserProfileDao
import com.replog.app.data.local.UserProfileEntity
import com.replog.app.domain.model.ActivityEvent
import com.replog.app.domain.model.ActivityType
import com.replog.app.domain.model.Goals
import com.replog.app.domain.model.GoalType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

fun ActivityEventEntity.toDomain() = ActivityEvent(
    id = id,
    epochDay = epochDay,
    type = ActivityType.from(type),
    intensity = intensity,
    title = title,
    detail = detail,
    value = value,
    createdAtMillis = createdAtMillis
)

@Singleton
class ActivityRepository @Inject constructor(private val activityDao: ActivityDao) {

    suspend fun record(
        type: ActivityType,
        epochDay: Long,
        intensity: Int,
        title: String,
        detail: String? = null,
        value: Double? = null
    ) = activityDao.insert(
        ActivityEventEntity(
            epochDay = epochDay,
            type = type.name,
            intensity = intensity,
            title = title,
            detail = detail,
            value = value,
            createdAtMillis = System.currentTimeMillis()
        )
    )

    fun observeRange(from: Long, to: Long): Flow<List<ActivityEvent>> =
        activityDao.observeBetween(from, to).map { list -> list.map { it.toDomain() } }

    fun observeDay(day: Long): Flow<List<ActivityEvent>> =
        activityDao.observeForDay(day).map { list -> list.map { it.toDomain() } }

    suspend fun listRange(from: Long, to: Long): List<ActivityEvent> =
        activityDao.listBetween(from, to).map { it.toDomain() }

    suspend fun isEmpty(): Boolean = activityDao.totalCount() == 0
}

@Singleton
class ProfileRepository @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val goalDao: GoalDao
) {
    fun observeProfile(): Flow<UserProfileEntity?> = userProfileDao.observe()

    fun observeGoals(): Flow<Goals> = goalDao.observe().map { it?.toGoals() ?: Goals() }

    suspend fun goals(): Goals = goalDao.get()?.toGoals() ?: Goals()

    suspend fun profile(): UserProfileEntity? = userProfileDao.get()

    suspend fun ensureDefaults(defaultName: String) {
        if (userProfileDao.get() == null) {
            userProfileDao.upsert(
                UserProfileEntity(id = 1L, name = defaultName, createdAtMillis = System.currentTimeMillis())
            )
        }
        if (goalDao.get() == null) {
            goalDao.upsert(GoalEntity(id = 1L, updatedAtMillis = System.currentTimeMillis()))
        }
    }

    suspend fun updateName(name: String) {
        val current = userProfileDao.get()
            ?: UserProfileEntity(id = 1L, name = name, createdAtMillis = System.currentTimeMillis())
        userProfileDao.upsert(current.copy(name = name))
    }

    suspend fun updateEmail(email: String?) {
        val current = userProfileDao.get()
            ?: UserProfileEntity(id = 1L, name = "Athlete", createdAtMillis = System.currentTimeMillis())
        userProfileDao.upsert(current.copy(email = email))
    }

    suspend fun updateGoals(goals: Goals) {
        goalDao.upsert(goals.toEntity(System.currentTimeMillis()))
    }
}

fun GoalEntity.toGoals() = Goals(
    goalType = GoalType.entries.firstOrNull { it.name == goalType } ?: GoalType.GENERAL_FITNESS,
    calorieTarget = calorieTarget,
    proteinTargetG = proteinTargetG,
    carbsTargetG = carbsTargetG,
    fatTargetG = fatTargetG,
    fiberTargetG = fiberTargetG,
    waterTargetMl = waterTargetMl,
    stepTarget = stepTarget,
    workoutDaysPerWeek = workoutDaysPerWeek
)

fun Goals.toEntity(now: Long) = GoalEntity(
    id = 1L,
    goalType = goalType.name,
    calorieTarget = calorieTarget,
    proteinTargetG = proteinTargetG,
    carbsTargetG = carbsTargetG,
    fatTargetG = fatTargetG,
    fiberTargetG = fiberTargetG,
    waterTargetMl = waterTargetMl,
    stepTarget = stepTarget,
    workoutDaysPerWeek = workoutDaysPerWeek,
    updatedAtMillis = now
)
