(() => {
    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (!prefersReduced) {
        const glow = document.createElement('div');
        glow.className = 'cursor-glow';
        document.body.appendChild(glow);

        window.addEventListener('pointermove', (event) => {
            glow.style.left = `${event.clientX}px`;
            glow.style.top = `${event.clientY}px`;
        }, {passive: true});
    }

    if (document.body.classList.contains('admin-page')) {
        document.querySelectorAll('.stat, .overview-status, .section').forEach((el, index) => {
            el.classList.add('reveal');
            if (!el.dataset.delay) el.dataset.delay = String(Math.min(index * 35, 260));
        });
    }

    const revealItems = [...document.querySelectorAll('.reveal')];

    if ('IntersectionObserver' in window) {
        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (!entry.isIntersecting) return;
                const delay = Number(entry.target.dataset.delay || 0);
                window.setTimeout(() => entry.target.classList.add('visible'), delay);
                observer.unobserve(entry.target);
            });
        }, {threshold: .08});

        revealItems.forEach(item => observer.observe(item));
    } else {
        revealItems.forEach(item => item.classList.add('visible'));
    }

    if (!prefersReduced && window.matchMedia('(pointer:fine)').matches) {
        document.querySelectorAll('.tilt').forEach(card => {
            card.addEventListener('pointermove', event => {
                const rect = card.getBoundingClientRect();
                const x = (event.clientX - rect.left) / rect.width - .5;
                const y = (event.clientY - rect.top) / rect.height - .5;
                card.style.transform = `perspective(950px) rotateX(${-y * 3.2}deg) rotateY(${x * 4.2}deg) translateY(-2px)`;
            });

            card.addEventListener('pointerleave', () => {
                card.style.transform = '';
            });
        });
    }

    window.V5Motion = {
        countTo(element, target, decimals = 0, duration = 850) {
            if (!element) return;
            const number = Number(target);
            if (!Number.isFinite(number)) {
                element.textContent = '--';
                return;
            }

            if (prefersReduced) {
                element.textContent = number.toFixed(decimals);
                return;
            }

            const start = performance.now();
            const from = Number(element.dataset.current || 0);

            const tick = now => {
                const progress = Math.min(1, (now - start) / duration);
                const eased = 1 - Math.pow(1 - progress, 3);
                const value = from + (number - from) * eased;
                element.textContent = value.toFixed(decimals);

                if (progress < 1) {
                    requestAnimationFrame(tick);
                } else {
                    element.dataset.current = String(number);
                }
            };

            requestAnimationFrame(tick);
        },

        pulse(element) {
            if (!element || prefersReduced) return;
            element.animate([
                {transform: 'scale(1)'},
                {transform: 'scale(1.025)'},
                {transform: 'scale(1)'}
            ], {duration: 360, easing: 'ease-out'});
        }
    };
})();
