package com.vasilevkin.musicplayer.base

import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity


abstract class BaseActivity<P : BaseContract.Presenter> : AppCompatActivity(), BaseContract.View {

    abstract val presenter: P

    abstract val progressBar: ProgressBar

    override fun showLoading() {
        progressBar.visibility = View.VISIBLE
    }

    override fun hideLoading() {
        progressBar.visibility = View.GONE
    }

}