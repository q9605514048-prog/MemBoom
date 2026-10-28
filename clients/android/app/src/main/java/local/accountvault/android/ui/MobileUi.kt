package local.accountvault.android.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.*
import local.accountvault.android.AndroidThemePolicy as Theme

object MobileUi {
    fun dp(view:android.view.View,n:Int)=(n*view.resources.displayMetrics.density).toInt()
    fun shape(view:android.view.View,color:String=Theme.panel)=GradientDrawable().apply{setColor(Color.parseColor(color));cornerRadius=dp(view,16).toFloat();setStroke(dp(view,1),Color.parseColor(Theme.panelStrong))}
    fun button(view:Button,primary:Boolean=false){
        view.isAllCaps=false;view.minHeight=dp(view,48);view.minimumWidth=0;view.setTextColor(Color.parseColor(Theme.text));view.textSize=14f;view.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        view.background=RippleDrawable(ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),shape(view,if(primary)Theme.magicRed else Theme.panelStrong),null)
        view.setPadding(dp(view,16),dp(view,10),dp(view,16),dp(view,10));view.stateListAnimator=null
    }
    fun field(view:EditText){
        view.setTextColor(Color.parseColor(Theme.text));view.setHintTextColor(Color.parseColor(Theme.mutedText));view.minHeight=dp(view,50)
        view.background=StateListDrawable().apply{addState(intArrayOf(android.R.attr.state_focused),shape(view).apply{setStroke(dp(view,1),Color.parseColor(Theme.magicGold))});addState(intArrayOf(),shape(view))}
        view.setPadding(dp(view,14),dp(view,12),dp(view,14),dp(view,12));view.textSize=15f
    }
    fun title(view:TextView){view.setTextColor(Color.parseColor(Theme.text));view.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)}
    fun keyboardInsets(view:android.view.View){
        if(android.os.Build.VERSION.SDK_INT<30)return
        var previousBottom=-1
        view.setOnApplyWindowInsetsListener{v,insets->
            val bottom=maxOf(insets.getInsets(android.view.WindowInsets.Type.ime()).bottom,insets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom)
            v.setPadding(v.paddingLeft,v.paddingTop,v.paddingRight,bottom)
            if(bottom!=previousBottom){previousBottom=bottom;v.post{v.findFocus()?.let{it.requestRectangleOnScreen(android.graphics.Rect(0,0,it.width,it.height),false)}}}
            insets
        }
    }
    fun quiet(view:Button){button(view);view.background=RippleDrawable(ColorStateList.valueOf(Color.parseColor("#20FFFFFF")),null,null);view.setTextColor(Color.parseColor(Theme.mutedText));view.textSize=12f}
    fun section(context:android.content.Context,title:String,subtitle:String,vararg children:android.view.View)=LinearLayout(context).apply{
        orientation=LinearLayout.VERTICAL;background=shape(this);val pad=dp(this,16);setPadding(pad,pad,pad,pad)
        addView(TextView(context).apply{text=title;textSize=18f;MobileUi.title(this);setPadding(0,0,0,dp(this,6))})
        if(subtitle.isNotBlank())addView(TextView(context).apply{text=subtitle;textSize=13f;setTextColor(Color.parseColor(Theme.mutedText));setPadding(0,0,0,dp(this,12))})
        val gap=dp(this,6)
        children.forEach{child->addView(child,LinearLayout.LayoutParams(-1,-2).apply{topMargin=if(child is TextView&&child !is EditText&&child !is Button)0 else gap})}
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=pad}
    }
    fun navigation(view:Button,index:Int,selected:Boolean){
        button(view);view.textSize=11f;view.gravity=android.view.Gravity.CENTER
        view.setPadding(dp(view,4),dp(view,7),dp(view,4),dp(view,6))
        val color=if(selected)Theme.magicRed else Theme.mutedText
        view.setTextColor(Color.parseColor(color));view.background=RippleDrawable(ColorStateList.valueOf(Color.parseColor("#20FFFFFF")),if(selected)shape(view,"#35212A") else null,null)
        view.setCompoundDrawables(null,TabIcon(index,Color.parseColor(color)).apply{setBounds(0,0,dp(view,22),dp(view,22))},null,null);view.compoundDrawablePadding=dp(view,4)
        view.isSelected=selected
    }
    private class TabIcon(val index:Int,val color:Int):Drawable(){
        private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=this@TabIcon.color;style=Paint.Style.STROKE;strokeWidth=1.8f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        override fun draw(canvas:Canvas){
            canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
            when(index){
                0->{canvas.drawRoundRect(3f,4f,21f,20f,3f,3f,pen);canvas.drawLine(3f,9f,21f,9f,pen);canvas.drawCircle(16f,14.5f,1.3f,pen)}
                1->{canvas.drawRoundRect(3f,3f,21f,21f,6f,6f,pen);canvas.drawLine(7f,12f,17f,12f,pen);canvas.drawLine(12f,7f,12f,17f,pen)}
                2->{canvas.drawRoundRect(5f,3f,20f,21f,2f,2f,pen);canvas.drawLine(3f,8f,7f,8f,pen);canvas.drawLine(3f,15f,7f,15f,pen);canvas.drawLine(10f,9f,16f,9f,pen);canvas.drawLine(10f,14f,16f,14f,pen)}
                else->{canvas.drawCircle(12f,12f,7f,pen);canvas.drawCircle(12f,12f,2.5f,pen);for(i in 0..7){canvas.save();canvas.rotate(i*45f,12f,12f);canvas.drawLine(12f,2f,12f,5f,pen);canvas.restore()}}
            };canvas.restore()
        }
        override fun setAlpha(alpha:Int){pen.alpha=alpha};override fun setColorFilter(filter:ColorFilter?){pen.colorFilter=filter}
        @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
}
