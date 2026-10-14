/**
 * SoukLine Interactive Client-Side JavaScript
 */
document.addEventListener('DOMContentLoaded', function () {

    // Quick Add to Cart via Fetch
    const quickAddButtons = document.querySelectorAll('.btn-quick-add');
    quickAddButtons.forEach(btn => {
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            const productId = this.getAttribute('data-product-id');
            const originalText = this.innerHTML;
            this.innerHTML = 'Ajout...';
            this.disabled = true;

            const formData = new FormData();
            formData.append('productId', productId);
            formData.append('quantity', 1);

            fetch('/panier/api/ajouter', {
                method: 'POST',
                body: formData
            })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    // Mettre à jour le badge du panier
                    const badges = document.querySelectorAll('.badge-count');
                    badges.forEach(b => {
                        b.textContent = data.cartCount;
                        b.classList.add('animate-pulse');
                    });

                    this.innerHTML = '✔ Ajouté !';
                    this.style.background = '#10B981';
                    this.style.color = '#FFF';

                    setTimeout(() => {
                        this.innerHTML = originalText;
                        this.style.background = '';
                        this.style.color = '';
                        this.disabled = false;
                    }, 1200);
                } else {
                    this.innerHTML = 'Erreur';
                    this.disabled = false;
                }
            })
            .catch(err => {
                console.error('Erreur ajout panier:', err);
                this.innerHTML = originalText;
                this.disabled = false;
            });
        });
    });

    // Copier une commande curl
    const copyBtns = document.querySelectorAll('.btn-copy-code');
    copyBtns.forEach(btn => {
        btn.addEventListener('click', function () {
            const code = this.getAttribute('data-code');
            if (code) {
                navigator.clipboard.writeText(code).then(() => {
                    const original = this.textContent;
                    this.textContent = 'Copié !';
                    setTimeout(() => { this.textContent = original; }, 1500);
                });
            }
        });
    });
});
