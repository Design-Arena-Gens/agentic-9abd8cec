package com.max.pandaai.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.max.pandaai.R
import com.max.pandaai.data.ChatMessage
import com.max.pandaai.databinding.ItemChatMessageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Binds conversation history to the RecyclerView in a chat-friendly layout.
class ChatAdapter(
    private val context: Context
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun submitList(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemChatMessageBinding.inflate(inflater, parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class ChatViewHolder(
        private val binding: ItemChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) = with(binding) {
            messageText.text = message.content
            messageTimestamp.text = dateFormat.format(Date(message.timestamp))

            val layoutParams = messageCard.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            val avatarParams = avatar.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams

            if (message.fromUser) {
                avatar.visibility = View.GONE
                layoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                layoutParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.startToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                layoutParams.endToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                layoutParams.horizontalBias = 1f
                avatarParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                messageCard.setCardBackgroundColor(
                    MaterialColors.getColor(messageCard, com.google.android.material.R.attr.colorPrimaryContainer)
                )
                messageText.setTextColor(
                    MaterialColors.getColor(messageCard, com.google.android.material.R.attr.colorOnPrimaryContainer)
                )
            } else {
                avatar.visibility = View.VISIBLE
                avatarParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                layoutParams.startToEnd = avatar.id
                layoutParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                layoutParams.endToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                layoutParams.horizontalBias = 0f
                messageCard.setCardBackgroundColor(
                    MaterialColors.getColor(messageCard, com.google.android.material.R.attr.colorSurfaceVariant)
                )
                messageText.setTextColor(MaterialColors.getColor(messageCard, com.google.android.material.R.attr.colorOnSurface))
            }

            messageCard.layoutParams = layoutParams
            avatar.layoutParams = avatarParams
        }
    }
}
