package com.example.dualworld.domain

import android.content.Context
import com.example.dualworld.data.CloneRepository

/**
 * UseCase coordinating pre-launch verification, agent binding, and execution.
 */
class LaunchCloneUseCase(private val repository: CloneRepository) {

    operator fun invoke(packageName: String, userId: Int = 0): Boolean {
        return repository.launchClone(packageName, userId)
    }
}
