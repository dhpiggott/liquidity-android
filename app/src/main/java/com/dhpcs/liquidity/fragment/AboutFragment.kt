package com.dhpcs.liquidity.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.dhpcs.liquidity.R
import de.psdev.licensesdialog.NoticesXmlParser
import kotlinx.android.synthetic.main.fragment_about.*

class AboutFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val notices = NoticesXmlParser.parse(resources.openRawResource(R.raw.licences)).notices

        for (notice in notices) {
            val linearLayoutAcknowledgement = layoutInflater.inflate(
                    R.layout.linearlayout_acknowledgement,
                    linearlayout_licences,
                    false
            ) as LinearLayout

            val textViewName = linearLayoutAcknowledgement
                    .findViewById<TextView>(R.id.textview_name)
            val textViewUrl = linearLayoutAcknowledgement
                    .findViewById<TextView>(R.id.textview_url)
            val textViewCopyrightAndLicense = linearLayoutAcknowledgement
                    .findViewById<TextView>(R.id.textview_copyright_and_license)

            textViewName.text = notice.name

            val url = notice.url
            if (url == null) textViewUrl.visibility = TextView.GONE else textViewUrl.text = url

            val copyright = notice.copyright
            val licenseSummary = notice.license.getSummaryText(requireContext())
            textViewCopyrightAndLicense.text = if (copyright == null) {
                licenseSummary
            } else {
                getString(
                        R.string.acknowledgement_copyright_and_license_format_string,
                        copyright,
                        licenseSummary
                )
            }

            linearlayout_licences!!.addView(linearLayoutAcknowledgement)
        }
    }

}
