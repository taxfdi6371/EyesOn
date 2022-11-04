package com.d201.domain.usecase.complaints

import com.d201.domain.model.Complaints
import com.d201.domain.repository.ComplaintsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompleteCompUseCase @Inject constructor(private val complaintsRepository: ComplaintsRepository) {
    fun excute(complaints: Complaints) = complaintsRepository.completeComplaints(complaints)
}