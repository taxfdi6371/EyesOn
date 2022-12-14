package com.d201.eyeson.view.blind.notification

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.d201.domain.model.Noti
import com.d201.domain.usecase.noti.DeleteNotiUseCase
import com.d201.domain.usecase.noti.SelectAllNotiUseCase
import com.d201.domain.utils.ResultType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BlindNotiViewModel"

@HiltViewModel
class BlindNotiViewModel @Inject constructor(
    private val selectAllNotiUseCase: SelectAllNotiUseCase,
    private val deleteNotiUseCase: DeleteNotiUseCase
) : ViewModel() {

    private val _notis: MutableStateFlow<List<Noti>> =
        MutableStateFlow(listOf())
    val notis get() = _notis.asStateFlow()

    fun selectAllNotis() {
        viewModelScope.launch(Dispatchers.IO) {
            selectAllNotiUseCase.execute().collectLatest {
                if (it is ResultType.Success) {
                    _notis.value = it.data
                } else if(it is ResultType.Empty) {
                    _notis.value = listOf()
                }else{
                    Log.d(TAG, "selectAllNotis: Error")
                }

            }
        }
    }

    fun deleteNoti(noti: Noti) {
        viewModelScope.launch(Dispatchers.IO) {
            deleteNotiUseCase.execute(noti)
            selectAllNotis()
        }
    }
}