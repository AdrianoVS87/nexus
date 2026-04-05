CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    user_id UUID,
    type VARCHAR(100) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_order_id ON notifications(order_id);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
