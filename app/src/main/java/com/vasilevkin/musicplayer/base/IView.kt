package com.vasilevkin.musicplayer.base


interface IView {
    fun showLoading()
    fun hideLoading()
    fun showError(msg: String)
}