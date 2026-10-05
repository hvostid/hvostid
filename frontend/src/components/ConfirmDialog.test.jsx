import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ConfirmDialog from './ConfirmDialog';

describe('confirmation dialog', () => {
    it('traps focus, closes on Escape and restores the trigger focus', () => {
        const trigger = document.createElement('button');
        document.body.append(trigger);
        trigger.focus();
        const close = vi.fn();
        const { unmount } = render(
            <ConfirmDialog
                isOpen
                onClose={close}
                onConfirm={() => {}}
                title="Delete passport"
                message="This cannot be undone"
            />
        );
        const dialog = screen.getByRole('dialog', { name: 'Delete passport' });
        expect(dialog).toHaveAttribute('aria-modal', 'true');
        const buttons = screen.getAllByRole('button');
        fireEvent.keyDown(document, { key: 'Tab', shiftKey: true });
        expect(buttons.at(-1)).toHaveFocus();
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(close).toHaveBeenCalledOnce();
        unmount();
        expect(trigger).toHaveFocus();
        trigger.remove();
    });
    it('cannot be dismissed while the operation is running', () => {
        const close = vi.fn();
        const { container } = render(
            <ConfirmDialog
                isOpen
                isLoading
                onClose={close}
                onConfirm={() => {}}
                title="Saving"
                message="Please wait"
            />
        );
        fireEvent.keyDown(document, { key: 'Escape' });
        fireEvent.click(container.querySelector('.backdrop-blur-sm'));
        expect(close).not.toHaveBeenCalled();
    });
});
