import { useParams } from 'react-router-dom';

export default function OrderTracking() {
  const { id } = useParams();
  return (
    <div>
      <h1 className="text-3xl font-bold mb-8">Order Tracking</h1>
      <p className="text-gray-400">Tracking order: {id}</p>
    </div>
  );
}
