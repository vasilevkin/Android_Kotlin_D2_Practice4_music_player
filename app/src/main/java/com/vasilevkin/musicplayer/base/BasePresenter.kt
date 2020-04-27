package com.vasilevkin.musicplayer.base


abstract class BasePresenter<V : BaseContract.View> : BaseContract.Presenter {

    open var view: V? = null

    override fun onViewCreated() {}

    override fun onSubscribe() {}

    override fun onUnsubscribe() {}

    @Suppress("UNCHECKED_CAST")
    override fun bindView(view: IView) {
        this.view = view as V
    }

    override fun unbindView() {
        this.view = null
    }
}