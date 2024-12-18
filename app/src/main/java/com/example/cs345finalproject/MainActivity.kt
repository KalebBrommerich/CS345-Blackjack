package com.example.cs345finalproject

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener  {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView

    private lateinit var deck: MutableList<Card>
    private lateinit var playerHand: MutableList<Card>
    private lateinit var dealerHand: MutableList<Card>
    private var casinoMode = false;
    private var playerBet = 0
    private var playerAceCount = 0
    private var dealerAceCount = 0
    private var playerScore = 0
    private var dealerScore = 0
    private var playerChips = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //setup the toolbar so it can have a textView
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        //find the drawerLayout and navigationView
        drawerLayout = findViewById(R.id.DrawerLayout)
        navigationView = findViewById(R.id.navigationview)

        //setup the drawer
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navOpen, R.string.navClose
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        //set the navigation view
        navigationView.setNavigationItemSelectedListener(this)

        //load the default fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.framelayout, Game()).commit()
            navigationView.setCheckedItem(R.id.game)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (toggle.onOptionsItemSelected(item)) true else super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem):Boolean{
        when(item.itemId){
            R.id.home -> {
                supportFragmentManager.beginTransaction().replace(R.id.framelayout,Home()).commit()
            }
            R.id.howtoplay -> {
                supportFragmentManager.beginTransaction().replace(R.id.framelayout,HowToPlay()).commit()
            }
            R.id.game -> {
                supportFragmentManager.beginTransaction().replace(R.id.framelayout,Game()).commitNow()
                //make a way to restore the game if nav away? viewmodel?
            }
            R.id.settings -> {
                supportFragmentManager.beginTransaction().replace(R.id.framelayout,Settings()).commitNow()
                findViewById<CheckBox>(R.id.casinoModeCheckbox).isChecked = casinoMode
            }
            R.id.addMoreChips -> {
                //give the player chips if they have none
                if(playerChips <= 0) {
                    playerChips += 1000
                    updateChipCounter()
                }
                //intent for google play store
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/")))
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    fun createNewGame(v: View) {
        //make the player get chips if they have none

        if (playerChips <= 0) {
            Toast.makeText(this, "You need to add chips to play.", Toast.LENGTH_SHORT).show()
            return
        }

        playerScore = 0
        playerAceCount = 0
        dealerScore = 0
        dealerAceCount = 0

        //initialize the dialog popup for the playerBet
        val dialogBuilder = android.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle("Place Your Bet")

        //setup the input field for the user
        val inputField = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Enter your bet"
        }
        dialogBuilder.setView(inputField)

        dialogBuilder.setPositiveButton("Confirm") { dialog, _ ->
            val betInput = inputField.text.toString()
            playerBet = betInput.toInt()

            //validate the playerBet and proceed
            if (playerBet in 1..playerChips && (playerBet % 10 == 0 || playerBet % 25 == 0)) {
                Toast.makeText(this, "Bet placed: $playerBet chips", Toast.LENGTH_SHORT).show()

                //lower the player's chips after the bet was cast
                playerChips -= playerBet

                //update the chip counter to accurate display the total
                updateChipCounter()

                //set visibility for the buttons to allow the game to proceed
                supportFragmentManager.beginTransaction().replace(R.id.framelayout, Game()).commitNow()
                findViewById<Button>(R.id.hitBtn).isVisible = true
                findViewById<Button>(R.id.standBtn).isVisible = true
                findViewById<Button>(R.id.doubleDownBtn).isVisible = true
                findViewById<Button>(R.id.doubleDownBtn).isEnabled = playerBet!! < playerChips
                findViewById<Button>(R.id.newGameAfterGameBtn).isVisible = false

                //create the shuffled deck
                deck = createDeck()

                //initialize the player hand
                playerHand = initializeHand()
                findViewById<ImageView>(R.id.playerCard1).setImageResource(playerHand[0].getImageResource(this))
                findViewById<ImageView>(R.id.playerCard2).setImageResource(playerHand[1].getImageResource(this))

                //initialize the dealer hand
                dealerHand = initializeHand()
                findViewById<ImageView>(R.id.dealerCard1).setImageResource(R.drawable.back)
                findViewById<ImageView>(R.id.dealerCard2).setImageResource(dealerHand[1].getImageResource(this))

                //initialize playerScore
                for (card in playerHand) {
                    playerAceCount += if (card.isAce) 1 else 0
                    playerScore += card.getNumberValue()
                }
                //initialize dealerScore
                for (card in dealerHand) {
                    dealerAceCount += if (card.isAce) 1 else 0
                    dealerScore += card.getNumberValue()
                }

                if (dealerScore == 21) {
                    stand(v)
                }
            } else {
                Toast.makeText(this, "Invalid bet. Please enter a value less than or equal to your chips and a multiple of 10 or 25.", Toast.LENGTH_SHORT).show()
                createNewGame(v) //reopen the dialog for a valid input
            }
            dialog.dismiss()
        }

        dialogBuilder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
            Toast.makeText(this, "Bet canceled. Game not started.", Toast.LENGTH_SHORT).show()
        }

        //show the dialog
        dialogBuilder.create().show()
    }

    fun hit(v: View){
        //add a card to the player's hand
        addCardToView(true, getCard())

        //disable the double down button after pressing hit
        val doubleDownButton = findViewById<Button>(R.id.doubleDownBtn)
        doubleDownButton.isEnabled = false

        //check if the player score is above 21 to disable the hit button
        if (reducePlayerAce() >= 21) {
            val hitButton = findViewById<Button>(R.id.hitBtn)
            hitButton.isEnabled = false
        }

    }

    fun stand(v: View){
        //disable the hit button after the player stands
        val hitButton = findViewById<Button>(R.id.hitBtn)
        hitButton.isEnabled = false

        //disable the double down button after the player stands
        val doubleDownButton = findViewById<Button>(R.id.doubleDownBtn)
        doubleDownButton.isEnabled = false

        //display the dealers unturned card
        findViewById<ImageView>(R.id.dealerCard1).setImageResource(dealerHand[0].getImageResource(this))

        if(casinoMode && reducePlayerAce()<=21){
            if(reduceDealerAce() >= 17){
                //dealer has 17 or more, adjust hidden card to "give the house and edge"
                while(reduceDealerAce() >= 17 && reduceDealerAce()+11 > reducePlayerAce()) {
                    dealerScore -= dealerHand[0].getNumberValue()
                    dealerHand[0] = getCard()
                    dealerScore += dealerHand[0].getNumberValue()
                }
            }
            //dealer can still hit
            while(reducePlayerAce()+1 > reduceDealerAce()||
                (reducePlayerAce() == 21 && reduceDealerAce()!=21)
                || reduceDealerAce() <= 17){
                try {
                    val candidateCard = getCard()
                    if(candidateCard.getNumberValue()+reduceDealerAce() > 21 ||
                        (candidateCard.getNumberValue()+reduceDealerAce()<reducePlayerAce()+1 &&
                                candidateCard.getNumberValue()+reduceDealerAce()>=17)){
                        continue
                    }
                    addCardToView(false, candidateCard)
                }catch (e: NoSuchElementException){
                    break;
                } //well let them have this one...
            }
        }else{
            //don't rig the game
            while (reduceDealerAce() < 17 && reducePlayerAce() <= 21){
                addCardToView(false, getCard())
            }
        }
        //dealer gets cards if the player hasn't busted and dealer is below 17
        endGame() //determines who won the game

        //adjust button visibility after game has finished
        hitButton.isVisible = false
        findViewById<Button>(R.id.standBtn).isVisible = false
        doubleDownButton.isVisible = false
        findViewById<Button>(R.id.newGameAfterGameBtn).isVisible = true
        findViewById<ImageView>(R.id.dealerCard1).setImageResource(dealerHand[0].getImageResource(this))

    }

    fun doubleDown(v: View){
        //lower the player's chips
        playerChips -= playerBet!!

        //double the player's bet
        playerBet = playerBet!! * 2

        //update the chip counter after doubling down
        updateChipCounter()

        //add a card to the player's hand
        addCardToView(true, getCard())

        //disable the hit button
        val hitButton = findViewById<Button>(R.id.hitBtn)
        hitButton.isEnabled = false

        //disable the double down button
        val doubleDownButton = findViewById<Button>(R.id.doubleDownBtn)
        doubleDownButton.isEnabled = false
    }

    fun casinoModeToggled(v: View){
        casinoMode = findViewById<CheckBox>(R.id.casinoModeCheckbox).isChecked
    }

    fun textSizeUpdated(v: View){
        Toast.makeText(this, "text size updated", Toast.LENGTH_SHORT).show()
    }

    private fun initializeHand(): MutableList<Card>{
        val list: MutableList<Card> = mutableListOf()
        list.add(deck.removeFirst()) //add the first card from the deck to the hand
        list.add(deck.removeFirst()) //add the second card from the deck to the hand
        return list //return the first two cards
    }

    // method to reduce the player's score if they go over 21 and have an ace in their hand
    private fun reducePlayerAce(): Int {
        while (playerScore > 21 && playerAceCount > 0) {
            playerScore -= 10
            playerAceCount -= 1
        }
        return playerScore
    }

    // method to reduce the dealer's score if they go over 21 and have an ace in their hand
    private fun reduceDealerAce(): Int {
        while (dealerScore > 21 && dealerAceCount > 0) {
            dealerScore -= 10
            dealerAceCount -= 1
        }
        return dealerScore
    }

    private fun addCardToView(addToPlayer: Boolean, newCard: Card){
        lateinit var cards:LinearLayout
        val image = ImageView(this)

        if(addToPlayer) {
            playerHand.add(newCard) //add card to the player hand
            playerAceCount += if (newCard.isAce) 1 else 0 //increase the counter of Ace's in the player hand
            playerScore += newCard.getNumberValue() //update the players score
            cards = findViewById<LinearLayout>(R.id.playerCards)
            image.setImageResource(newCard.getImageResource(this))
        }
        else {
            dealerHand.add(newCard) //add card to the dealer hand
            dealerAceCount += if (newCard.isAce) 1 else 0 //increase the counter of Ace's in the dealer hand
            dealerScore += newCard.getNumberValue() //update the dealers score
            cards = findViewById<LinearLayout>(R.id.dealerCards)
            if(cards.childCount == 0){
                image.setImageResource(R.drawable.back)
            }else{
                image.setImageResource(newCard.getImageResource(this))
            }
        }
        cards.addView(image)
        for (card in cards.children) {
            (card as ImageView).layoutParams.width =
                cards.width / (if (cards.childCount == 0) 1 else cards.childCount)
        }
    }

    private fun createDeck(): MutableList<Card> {
        val suits = listOf("Hearts", "Diamonds", "Clubs", "Spades")
        val ranks = listOf("two", "three", "four", "five", "six", "seven", "eight", "nine",
                            "ten", "Jack", "Queen", "King", "Ace")

        return suits.flatMap { suit ->
            ranks.map { rank ->
                Card(rank, suit)
            }
        }.shuffled().toMutableList()
    }

    private fun getCard():Card {
        return deck.removeFirst()
    }

    private fun updateChipCounter(){
        val chipCounter = findViewById<TextView>(R.id.chip_counter)
        chipCounter.text = "Chips: $playerChips"
    }

    private fun endGame() {
        //both player and dealer have blackjack
        if(playerScore == 21 && dealerScore == 21){
            playerChips += playerBet!!
            Toast.makeText(this, "Pushed, $playerBet chips have been returned", Toast.LENGTH_LONG).show()
        } //player has blackjack and the dealer does not
        else if(playerScore == 21 && dealerScore != 21 && playerHand.size == 2) {
            val playerWinnings = (playerBet?.plus((playerBet!! *3)/2)!!)
            playerChips += playerWinnings
            Toast.makeText(this, "You have blackjack! You won $playerWinnings chips", Toast.LENGTH_LONG).show()
        } //dealer has blackjack and the player does not
        else if(dealerScore == 21 && playerScore != 21 && dealerHand.size == 2) {
            Toast.makeText(this, "Dealer has blackjack. You Lost", Toast.LENGTH_LONG).show()
        }//player has 21 but not blackjack
        else if(playerScore == 21 && dealerScore != 21) {
            val playerWinnings = playerBet!! * 2
            playerChips += playerWinnings
            Toast.makeText(this, "You won $playerWinnings chips", Toast.LENGTH_LONG).show()
        } //dealer has 21 and the player does not
        else if(dealerScore == 21 && playerScore != 21) {
            Toast.makeText(this, "Dealer has 21. You Lost", Toast.LENGTH_LONG).show()
        }//player busts
        else if(playerScore > 21) {
            Toast.makeText(this, "You busted. You Lost", Toast.LENGTH_LONG).show()
        } //dealer busts
        else if(dealerScore > 21) {
            val playerWinnings = playerBet!! * 2
            playerChips += playerWinnings
            Toast.makeText(this, "Dealer busted. You Won $playerWinnings chips", Toast.LENGTH_LONG).show()
        }//player and dealer have the same sum
        else if(playerScore == dealerScore) {
            playerChips += playerBet!!
            Toast.makeText(this, "Pushed, $playerBet chips have been returned", Toast.LENGTH_LONG).show()
        } //player has a greater hand than the dealer
        else if(playerScore > dealerScore) {
            val playerWinnings = playerBet!! * 2
            playerChips += playerWinnings
            Toast.makeText(this, "You Won $playerWinnings chips", Toast.LENGTH_LONG).show()
        } //dealer has a greater hand than the dealer
        else if(dealerScore > playerScore) {
            Toast.makeText(this, "You Lost", Toast.LENGTH_LONG).show()
        }

        //update the chip counter after the game was determined
        updateChipCounter()
    }
}