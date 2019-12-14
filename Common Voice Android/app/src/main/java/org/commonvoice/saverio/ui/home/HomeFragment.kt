package org.commonvoice.saverio.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_settings.*
import org.commonvoice.saverio.MainActivity
import org.commonvoice.saverio.R

class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
            ViewModelProviders.of(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        val btnSpeak: Button = root.findViewById(R.id.btn_speak)
        val btnListen: Button = root.findViewById(R.id.btn_listen)
        val btnLogin: Button = root.findViewById(R.id.btn_login)

        btnSpeak.setOnClickListener{
            (activity as MainActivity).open_speak_section()
        }

        btnListen.setOnClickListener{
            (activity as MainActivity).open_listen_section()
        }

        btnLogin.setOnClickListener{
            (activity as MainActivity).open_login_section()
        }

        return root
    }
}