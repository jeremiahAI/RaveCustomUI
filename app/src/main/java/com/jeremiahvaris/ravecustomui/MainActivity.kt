package com.jeremiahvaris.ravecustomui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.flutterwave.raveandroid.*
import com.flutterwave.raveandroid.card.CardContract
import com.flutterwave.raveandroid.card.CardFragment
import com.flutterwave.raveandroid.card.CardPresenter
import com.flutterwave.raveandroid.data.SavedCard
import com.flutterwave.raveandroid.responses.ChargeResponse
import com.flutterwave.raveandroid.responses.LookupSavedCardsResponse
import com.flutterwave.raveandroid.responses.RequeryResponse
import com.flutterwave.raveandroid.responses.SaveCardResponse
import com.flutterwave.raveandroid.verification.AVSVBVFragment
import com.flutterwave.raveandroid.verification.OTPFragment
import com.flutterwave.raveandroid.verification.PinFragment
import com.flutterwave.raveandroid.verification.VerificationActivity
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import dmax.dialog.SpotsDialog
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import java.util.*

class MainActivity : AppCompatActivity(), CardContract.View {
    private var payload: Payload? = null
    private lateinit var cvv: String
    private lateinit var expiryYear: String
    private lateinit var expiryMonth: String
    private lateinit var email: String
    private val currency: String="NGN"
    private var amount: String = "10.0"
    private val otp: String?="12345"
    private var flwRef: String?=null
    private val onStaging = true

    private val FOR_SAVED_CARDS = 777

    private val encryptionKey: String
        get() = if (onStaging) "FLWSECK_TEST24a907495c60"
        else "7b52e2b832ecbb4451fe7b3b"
    private val publicKey: String
        get() = if (onStaging) "FLWPUBK_TEST-7ddb1c9cb4571aa27d588f468fb8c052-X"
        else "FLWPUBK-aec2b6c6cfe500854a21a0808f1ca280-X"


    private val cardNumber: String
        get() = if (onStaging) "5531886652142950"
        else "5399830238662058"

    // Presenter defined inside SDK
    lateinit var presenter: CardPresenter

    private val progressDialog by lazy {
        SpotsDialog(this, "Please wait...", R.style.Custom)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the SDK
        val raver = RavePayManager(this)
            .onStagingEnv(onStaging).initializeNoUi()
        presenter = CardPresenter(this, this, raver.appComponent)

        // Put default values in the editTexts
        setUpDefaultValues()


        // Get values from form and pay
        pay_button.setOnClickListener {
            amount = amountEt.text.toString()
            email = emailEt.text.toString()
            expiryMonth = cardExpiryEt.text.toString().substring(0,2)
            expiryYear = cardExpiryEt.text.toString().substring(3)
            cvv = cvvEt.text.toString()
            pay()
        }
    }

    private fun setUpDefaultValues() {
        amountEt.setText("10")
        emailEt.setText("chairman@bossman.com")
        cardNoEt.setText(cardNumber)// This is a test card. See test cards here https://developer.flutterwave.com/v2.0/reference#test-cards-1
        cardExpiryEt.setText("06/20")
        cvvEt.setText("972")
    }

    private fun pay() {
        val builder = PayloadBuilder()// Set the payment details
        builder.setAmount(amount)
            .setCardno(cardNumber)
            .setCountry("NG")
            .setCurrency("NGN")
            .setCvv(cvv)
            .setEmail(email)
            .setFirstname("Wuraola")
            .setLastname("Benson")
            .setIP("")
            .setTxRef("1")
            .setExpiryyear(expiryYear)
            .setExpirymonth(expiryMonth)
            .setPBFPubKey(publicKey)
            .setDevice_fingerprint("")

        val body = builder.createPayload()

        // This is the start point of the payment
        presenter.chargeCard(body, encryptionKey)
    }

    /************************************************************************/
    // Essential functions to override for your use case

    /**
     * Called to show or hide some sort of progress bar to the user.
     * Like when a network call is being made by the SDK.
     * @param active whether or not the progress bar should be shown (true) or hidden (false).
     */
    override fun showProgressIndicator(active: Boolean) {
        if (active) {
            if (!progressDialog.isShowing && !isFinishing) {
                progressDialog.show()
            }
        } else {
            progressDialog.dismiss()
        }
    }

    /**
     * This function is called when we need to get the pin of the card.
     * Ideally, you'd get the Pin from the UI,
     * then pass to the pin to the presenter with the presenter's chargeCardWithSuggestedAuthModel function.
     */
    override fun onPinAuthModelSuggested(payload: Payload?) {
//        toast("onPinAuthModelSuggested called")
        this.payload = payload
        val intent = Intent(this, VerificationActivity::class.java)
        intent.putExtra(VerificationActivity.EXTRA_IS_STAGING, onStaging)
        intent.putExtra(VerificationActivity.PUBLIC_KEY_EXTRA, publicKey)
        intent.putExtra(VerificationActivity.ACTIVITY_MOTIVE, "pin")
        intent.putExtra("theme", R.style.DefaultTheme)
        startActivityForResult(intent, CardFragment.FOR_PIN)
    }
    ////        presenter.chargeCardWithSuggestedAuthModel(
//            payload,
//            "3310",
//            RaveConstants.PIN,
//            encryptionKey
//        )}

    /**
     * This function is called when we need to get the OTP
     * Ideally, you'd get the OTP from the UI,
     * then pass to the pin to the presenter with the presenter's validateCardCharge function.
     */
    override fun showOTPLayout(flwRef: String?, chargeResponseMessage: String?) {
        this.flwRef = flwRef
//        toast("showOTPLayout called")
        val intent = Intent(this, VerificationActivity::class.java)
        intent.putExtra(VerificationActivity.EXTRA_IS_STAGING, onStaging)
        intent.putExtra(VerificationActivity.PUBLIC_KEY_EXTRA, publicKey)
        intent.putExtra(OTPFragment.EXTRA_CHARGE_MESSAGE, chargeResponseMessage)
        intent.putExtra(VerificationActivity.ACTIVITY_MOTIVE, "otp")
        intent.putExtra("theme", R.style.DefaultTheme)
        startActivityForResult(intent, CardFragment.FOR_OTP)
    }

    /**
     * Called when the charge has been validated successfully.
     * The requery function I called here is to confirm the status from the server.
     */
    override fun onValidateSuccessful(message: String?, responseAsString: String?) {
        toast("onValidateSuccessful called")
        presenter.requeryTx(flwRef, publicKey)
    }

    /**
     * Called when the status has been confirmed successfully.
     * Here we used and inner helper class to check transaction status
     */
    override fun onRequerySuccessful(
        response: RequeryResponse?,
        responseAsJSONString: String?,
        flwRef: String?
    ) {
        val wasTxSuccessful: Boolean = TransactionStatusChecker(Gson())
            .getTransactionStatus(amount, currency, responseAsJSONString)

        if (wasTxSuccessful) {
            onPaymentSuccessful(response!!.status, flwRef, responseAsJSONString, null)
        } else {
            onPaymentFailed(response!!.status, responseAsJSONString)
        }
        toast("onRequerySuccessful called")
    }

    /**
     * Called when payment has been completed successfully.
     * Since you're saving cards, you can use the @param flwRef to call this endpoint to get the card token:
     * https://developer.flutterwave.com/v2.0/reference#xrequery-transaction-verification
     * The card token is what you will use for subsequent charges.
     * Check out the documentation here: https://developer.flutterwave.com/v2.0/reference#save-a-card
     *
     * Sha sha here, you can show the user that "Card saved successfully" or something.
     */
    override fun onPaymentSuccessful(
        status: String?,
        flwRef: String?,
        responseAsString: String?,
        ravePayInitializer: RavePayInitializer?
    ) {
        toast("onPaymentSuccessful called")
        showSnackBar("Payment successful",true)
    }


    /** Called if the payment fails
     *
     */
    override fun onPaymentFailed(status: String?, responseAsString: String?) {
        toast("onPaymentFailed called")
        showSnackBar("Payment successful",true)
    }

    /**
     * Called when there's some sort of error during the process.
     * Usually you'd just want to show a toast or something with the error message.
     */
    override fun onPaymentError(message: String?) {
        message?.let { toast(it) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RavePayActivity.RESULT_SUCCESS) { //just to be sure this v sent the receiving intent
            if (requestCode == CardFragment.FOR_PIN) {
                val pin = data?.getStringExtra(PinFragment.EXTRA_PIN)
                presenter.chargeCardWithSuggestedAuthModel(payload, pin, RaveConstants.PIN, encryptionKey)
            } else if (requestCode == CardFragment.FOR_AVBVV) {
                val address = data?.getStringExtra(AVSVBVFragment.EXTRA_ADDRESS)
                val state = data?.getStringExtra(AVSVBVFragment.EXTRA_STATE)
                val city = data?.getStringExtra(AVSVBVFragment.EXTRA_CITY)
                val zipCode = data?.getStringExtra(AVSVBVFragment.EXTRA_ZIPCODE)
                val country = data?.getStringExtra(AVSVBVFragment.EXTRA_COUNTRY)
                presenter.chargeCardWithAVSModel(
                    payload, address, city, zipCode, country, state,
                    RaveConstants.NOAUTH_INTERNATIONAL, encryptionKey
                )
            } else if (requestCode == CardFragment.FOR_INTERNET_BANKING) {
                presenter.requeryTx(flwRef, publicKey)
            } else if (requestCode == CardFragment.FOR_OTP) {
                val otp = data?.getStringExtra(OTPFragment.EXTRA_OTP)
                if (data!=null){
                    if (data.getBooleanExtra(OTPFragment.IS_SAVED_CARD_CHARGE, false)) {
                    payload?.setOtp(otp)
                    presenter.chargeSavedCard(payload, encryptionKey)
                } else presenter.validateCardCharge(flwRef, otp,publicKey)
                }

            }
//            else if (requestCode == FOR_SAVED_CARDS) {
//                if (data.hasExtra(SavedCardsFragment.EXTRA_SAVED_CARDS)) {
//                    val savedCardToCharge = Gson().fromJson(
//                        data.getStringExtra(SavedCardsFragment.EXTRA_SAVED_CARDS),
//                        SavedCard::class.java
//                    )
//                    onSavedCardSelected(savedCardToCharge)
//                }
//                presenter.checkForSavedCardsInMemory(ravePayInitializer)
//            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /*************************************************************************/

    // These functions, for now you don't need to handle them.
    // Since you're in a hurry, we can come back to these later.


    override fun onCardSaveSuccessful(
        response: SaveCardResponse?,
        responseAsJSONString: String?,
        phoneNumber: String?
    ) {
        toast("onCardSaveSuccessful called")
    }

    override fun showToast(message: String?) {
        toast("message")
    }

    override fun onNoAuthInternationalSuggested(payload: Payload?) {
        toast("onNoAuthInternationalSuggested called")
    }

    override fun onVBVAuthModelUsed(authUrlCrude: String?, flwRef: String?) {
        toast("onVBVAuthModelUsed called")
    }

    override fun onPhoneNumberValidated(phoneNumber: String?) {
        toast("onPhoneNumberValidated called")
    }

    override fun onAmountValidated(amountToSet: String?, visibility: Int) {
        toast("onAmountValidated called")
    }

    override fun showFetchFeeFailed(s: String?) {
        s?.let { toast(it) }
    }


    override fun showFieldError(viewID: Int, message: String?, viewtype: Class<*>?) {
        toast("showFieldError called")
    }

    override fun onTokenRetrievalError(s: String?) {
        toast("onTokenRetrievalError called")
    }

    override fun onValidateError(message: String?) {
        toast("onValidateError called")
    }

    override fun onCardSaveFailed(message: String?, responseAsJSONString: String?) {
        toast("onCardSaveFailed called")
    }

    override fun onEmailValidated(emailToSet: String?, visibility: Int) {
        toast("onEmailValidated called")
    }

    override fun showSavedCardsLayout(savedCardsList: MutableList<SavedCard>?) {
        toast("showSavedCardsLayout called")
    }

    override fun onNoAuthUsed(flwRef: String?, publicKey: String?) {
        toast("onNoAuthUsed called")
    }

    override fun onSendRaveOtpFailed(message: String?, responseAsJSONString: String?) {
        toast("onSendRaveOtpFailed called")
    }

    override fun showSavedCards(cards: MutableList<SavedCard>?) {
        toast("showSavedCards called")
    }

    override fun onValidateCardChargeFailed(flwRef: String?, responseAsJSON: String?) {
        toast("onValidateCardChargeFailed called")
    }

    override fun onChargeTokenComplete(response: ChargeResponse?) {
        toast("onChargeTokenComplete called")
    }

    override fun showCardSavingOption(b: Boolean) {
        toast("showCardSavingOption called")
    }

    override fun setHasSavedCards(b: Boolean) {
        toast("setHasSavedCards called")
    }

    override fun onAVSVBVSecureCodeModelUsed(authurl: String?, flwRef: String?) {
        toast("onAVSVBVSecureCodeModelUsed called")
    }

    override fun onValidationSuccessful(dataHashMap: HashMap<String, ViewObject>?) {
        toast("onValidationSuccessful called")
    }

    override fun displayFee(charge_amount: String?, payload: Payload?, why: Int) {
        toast("displayFee called")
    }

    override fun onChargeCardSuccessful(response: ChargeResponse?) {
        toast("onChargeCardSuccessful called")
    }

    override fun onAVS_VBVSECURECODEModelSuggested(payload: Payload?) {
        toast("onAVS_VBVSECURECODEModelSuggested called")
    }

    override fun onTokenRetrieved(flwRef: String?, cardBIN: String?, token: String?) {
        toast("onTokenRetrieved called")
    }

    override fun onLookupSavedCardsFailed(
        message: String?,
        responseAsJSONString: String?,
        verifyResponseAsJSONString: String?
    ) {
        toast("onLookupSavedCardsFailed called")
    }

    override fun onLookupSavedCardsSuccessful(
        response: LookupSavedCardsResponse?,
        responseAsJSONString: String?,
        verifyResponseAsJSONString: String?
    ) {
        toast("onLookupSavedCardsSuccessful called")
    }

    override fun showOTPLayoutForSavedCard(payload: Payload?, authInstruction: String?) {
        toast("showOTPLayoutForSavedCard called")
    }

    // Helper function to show snackbar
    private fun showSnackBar(message: String, static: Boolean) {
        var snackBarLength = Snackbar.LENGTH_SHORT

        if (static) snackBarLength = Snackbar.LENGTH_INDEFINITE

        val mySnackbar = Snackbar.make(
            pay_button,
            message, snackBarLength
        )
        mySnackbar.setActionTextColor(ContextCompat.getColor(this, R.color.white))

        mySnackbar.setAction("OK") {
            mySnackbar.dismiss()

        }

        mySnackbar.show()
    }
}
