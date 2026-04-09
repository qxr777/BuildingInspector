document.addEventListener('DOMContentLoaded', () => {
    // 缩略图切换
    const mainImg = document.getElementById('main-product-image');
    const thumbs = document.querySelectorAll('.thumb');

    thumbs.forEach(thumb => {
        thumb.addEventListener('mouseover', () => {
            thumbs.forEach(t => t.classList.remove('active'));
            thumb.classList.add('active');
            mainImg.src = thumb.src.replace('w=200', 'w=800');
        });
    });

    // 尺码选择
    const sizeChips = document.querySelectorAll('.size-chip');
    sizeChips.forEach(chip => {
        chip.addEventListener('click', () => {
            sizeChips.forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
        });
    });

    // 颜色选择
    const colorCircles = document.querySelectorAll('.color-circle');
    colorCircles.forEach(circle => {
        circle.addEventListener('click', () => {
            colorCircles.forEach(c => c.classList.remove('active'));
            circle.classList.add('active');
        });
    });

    // 购物车逻辑
    let count = 0;
    const cartCount = document.getElementById('cart-count');
    const addToCartBtn = document.getElementById('add-to-cart-btn');

    addToCartBtn.addEventListener('click', () => {
        count++;
        cartCount.textContent = count;
        
        // 简单的按钮动画
        addToCartBtn.textContent = '已加入';
        addToCartBtn.style.background = '#000';
        
        setTimeout(() => {
            addToCartBtn.textContent = '加入购物车';
            addToCartBtn.style.background = '';
        }, 2000);
        
        // 购物车图标震动
        const cartIcon = document.querySelector('.cart-icon');
        cartIcon.style.animation = 'shake 0.5s ease-in-out';
        setTimeout(() => {
            cartIcon.style.animation = '';
        }, 500);
    });
});
