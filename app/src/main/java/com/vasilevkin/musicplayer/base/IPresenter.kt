package com.vasilevkin.musicplayer.base


interface IPresenter {
    fun onViewCreated()
    fun onSubscribe()
    fun onUnsubscribe()
    fun bindView(view: IView)
    fun unbindView()
}