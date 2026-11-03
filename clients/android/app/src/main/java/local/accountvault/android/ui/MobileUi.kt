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
    fun shape(view:android.view.View,color:String=Theme.panel,radius:Int=Theme.cardRadius)=GradientDrawable().apply{setColor(Color.parseColor(color));cornerRadius=dp(view,radius).toFloat();setStroke(dp(view,1),Color.parseColor(Theme.border))}
    fun button(view:Button,primary:Boolean=false){
        view.isAllCaps=false;view.minHeight=dp(view,48);view.minimumWidth=0;view.setTextColor(states(Theme.text));view.textSize=14f;view.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        view.background=RippleDrawable(states(Theme.ripple),shape(view,if(primary)Theme.magicRed else Theme.panelStrong,Theme.controlRadius),null)
        view.setPadding(dp(view,16),dp(view,10),dp(view,16),dp(view,10));view.stateListAnimator=null
    }
    fun field(view:EditText){
        view.setTextColor(Color.parseColor(Theme.text));view.setHintTextColor(Color.parseColor(Theme.mutedText));view.minHeight=dp(view,56);view.typeface=Typeface.create("sans-serif",Typeface.NORMAL);view.importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO;view.isSaveEnabled=false
        view.background=StateListDrawable().apply{addState(intArrayOf(android.R.attr.state_focused),shape(view,Theme.inset,Theme.controlRadius).apply{setStroke(dp(view,1),Color.parseColor(Theme.magicGold))});addState(intArrayOf(),shape(view,Theme.inset,Theme.controlRadius))}
        view.setPadding(dp(view,14),dp(view,12),dp(view,14),dp(view,12));view.textSize=15f
    }
    fun title(view:TextView){view.setTextColor(Color.parseColor(Theme.text));view.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)}
    fun keyboardInsets(view:android.view.View){
        val left=view.paddingLeft;val top=view.paddingTop;val right=view.paddingRight;val bottom=view.paddingBottom
        view.setOnApplyWindowInsetsListener{v,insets->
            @Suppress("DEPRECATION")
            val barsTop=if(android.os.Build.VERSION.SDK_INT>=30)insets.getInsets(android.view.WindowInsets.Type.systemBars()).top else insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val barsBottom=if(android.os.Build.VERSION.SDK_INT>=30)maxOf(insets.getInsets(android.view.WindowInsets.Type.ime()).bottom,insets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom) else insets.systemWindowInsetBottom
            v.setPadding(left,top+barsTop,right,bottom+barsBottom)
            insets
        }
        view.requestApplyInsets()
    }
    fun quiet(view:Button){button(view);view.background=RippleDrawable(ColorStateList.valueOf(Color.parseColor("#20FFFFFF")),null,null);view.setTextColor(Color.parseColor(Theme.mutedText));view.textSize=14f}
    fun section(context:android.content.Context,title:String,subtitle:String,vararg children:android.view.View)=LinearLayout(context).apply{
        orientation=LinearLayout.VERTICAL;background=shape(this);val pad=dp(this,16);setPadding(pad,pad,pad,pad)
        addView(TextView(context).apply{text=title;textSize=18f;MobileUi.title(this);setPadding(0,0,0,dp(this,8))})
        if(subtitle.isNotBlank())addView(TextView(context).apply{text=subtitle;textSize=13f;setTextColor(Color.parseColor(Theme.mutedText));setPadding(0,0,0,dp(this,12))})
        val gap=dp(this,8)
        children.forEach{child->addView(child,LinearLayout.LayoutParams(-1,-2).apply{topMargin=if(child is TextView&&child !is EditText&&child !is Button)0 else gap})}
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=pad}
    }
    fun navigation(view:Button,index:Int,selected:Boolean){
        button(view);view.textSize=11f;view.gravity=android.view.Gravity.CENTER
        view.setPadding(dp(view,4),dp(view,7),dp(view,4),dp(view,6))
        val color=if(selected)Theme.text else Theme.mutedText
        view.setTextColor(Color.parseColor(color));view.background=RippleDrawable(ColorStateList.valueOf(Color.parseColor("#20FFFFFF")),if(selected)shape(view,Theme.panelStrong,Theme.controlRadius) else null,null)
        view.setCompoundDrawables(null,TabIcon(index,Color.parseColor(color)).apply{setBounds(0,0,dp(view,22),dp(view,22))},null,null);view.compoundDrawablePadding=dp(view,4)
        view.isSelected=selected;view.contentDescription=view.text;view.minHeight=dp(view,64)
    }
    private fun states(color:String)=ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(Color.parseColor(Theme.mutedText),Color.parseColor(color)))
    fun check(view:CompoundButton){
        view.minHeight=dp(view,48);view.textSize=14f;view.setTextColor(Color.parseColor(Theme.text))
        view.buttonTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(Color.parseColor(Theme.magicGold),Color.parseColor(Theme.mutedText)))
    }
    fun action(context:android.content.Context,text:String,primary:Boolean=false,onClick:()->Unit)=Button(context).apply{
        this.text=text;button(this,primary);setOnClickListener{onClick()}
    }
    fun text(context:android.content.Context,value:String,size:Float=14f,heading:Boolean=false)=TextView(context).apply{
        text=value;textSize=size;setTextColor(Color.parseColor(if(heading)Theme.text else Theme.mutedText))
        if(heading)title(this);setLineSpacing(dp(this,4).toFloat(),1f)
    }
    fun label(context:android.content.Context,value:String,field:EditText)=text(context,value,13f).apply{
        if(field.id==android.view.View.NO_ID)field.id=android.view.View.generateViewId()
        labelFor=field.id;setPadding(0,dp(this,8),0,dp(this,4))
    }
    fun stack(context:android.content.Context,padding:Int=0)=LinearLayout(context).apply{
        orientation=LinearLayout.VERTICAL;val p=dp(this,padding);setPadding(p,p,p,p)
    }
    fun gapParams(view:android.view.View,gap:Int=12)=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(view,gap)}
    fun danger(view:Button){button(view);view.setTextColor(Color.parseColor(Theme.danger))}
    fun cardClick(view:android.view.View,onClick:()->Unit){
        view.background=RippleDrawable(ColorStateList.valueOf(Color.parseColor(Theme.ripple)),shape(view),null)
        view.minimumHeight=dp(view,72);view.isFocusable=true;view.setOnClickListener{onClick()}
    }
    fun brand(context:android.content.Context)=LinearLayout(context).apply{
        gravity=android.view.Gravity.CENTER_VERTICAL;setPadding(0,dp(this,8),0,dp(this,20))
        val iconGap=dp(this,12)
        addView(android.view.View(context).apply{background=Sigil()},LinearLayout.LayoutParams(dp(this,40),dp(this,40)).apply{rightMargin=iconGap})
        addView(text(context,"MemBoom",23f,true))
    }
    private class Sigil:Drawable(){
        private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.4f;strokeJoin=Paint.Join.ROUND}
        override fun draw(canvas:Canvas){
            canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/40f,bounds.height()/40f)
            pen.color=Color.parseColor(Theme.magicGold);canvas.drawCircle(20f,20f,16f,pen)
            pen.color=Color.parseColor(Theme.magicRed)
            val path=android.graphics.Path().apply{moveTo(20f,3f);lineTo(24f,15f);lineTo(37f,20f);lineTo(24f,24f);lineTo(20f,37f);lineTo(16f,24f);lineTo(3f,20f);lineTo(16f,15f);close()};canvas.drawPath(path,pen)
            pen.color=Color.parseColor(Theme.text);canvas.drawLine(15f,23f,15f,17f,pen);canvas.drawLine(15f,17f,20f,22f,pen);canvas.drawLine(20f,22f,25f,17f,pen);canvas.drawLine(25f,17f,25f,23f,pen);canvas.restore()
        }
        override fun setAlpha(alpha:Int){pen.alpha=alpha};override fun setColorFilter(filter:ColorFilter?){pen.colorFilter=filter}
        @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
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
