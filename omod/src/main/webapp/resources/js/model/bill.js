define(
	[
		'model/generic',
		'model/payment',
		'model/lineItem'
	],
	function(openhmis) {
		openhmis.Bill = openhmis.GenericModel.extend({
			meta: {
				name: "Bill",
				namePlural: "Bills",
				openmrsType: 'data',
				restUrl: "bill"
			},
			
			schema: {
				lineItems: { type: 'Object'},
				patient: { type: 'Object' },
				payments: { type: 'Object'}
			},
						
			BillStatus: {
				PENDING:	"PENDING",
				PAID:		"PAID"
			},
			
			initialize: function(attrs, options) {
				openhmis.GenericModel.prototype.initialize.call(this, attrs, options);
				if (!this.get("lineItems")) this.set("lineItems",
					new openhmis.GenericCollection([], { model: openhmis.LineItem }), { silent: true });
				if (!this.get("payments")) this.set("payments",
					new openhmis.GenericCollection([], { model: openhmis.Payment }), { silent: true });
				if (!this.get("status")) this.set("status",
					this.BillStatus.PENDING, { silent: true });
			},
			
			addPayment: function(payment) {
				payment.meta.parentRestUrl = this.url() + '/';
				this.get("payments").add(payment);
			},
			
			getTotal: function() {
				var total = 0;
				var lineItems = this.get("lineItems");
				if (lineItems && lineItems.length > 0) {
					lineItems.each(function(item) {
						if (item.isClean()) total += item.getTotal();
					});
				}
				return total;
			},
			
			getTotalPaid: function() {
				var total = 0;
				var payments = this.get("payments");
				if (payments && payments.length > 0) {
					payments.each(function(payment) {
						if (payment.get("voided") !== true)
							total += payment.get("amount");
					});
				}
				return total;
			},
			
			validate: function(final) {
				// By default, backbone validates every time we try try to alter
				// the model.  We don't want to be bothered with this until we
				// care.
                if (final !== true) return null;
				
				if (this.get("patient") === undefined)
					return { patient: "A bill needs to be associated with a patient." }
				if (this.get("lineItems") === undefined || this.get("lineItems").length === 0)
					return { lineItems: "A bill should contain at least one item." }
				return null;
			},
			
			toJSON: function() {
				var attrs = openhmis.GenericModel.prototype.toJSON.call(this);
				if (attrs.lineItems) attrs.lineItems = attrs.lineItems.toJSON();
				if (attrs.patient) attrs.patient = attrs.patient.id;
				return attrs;
			},
			
			parse: function(resp) {
				if (resp === null) return resp;
				if (resp.lineItems) {
					resp.lineItems = new openhmis.GenericCollection(resp.lineItems, {
						model: openhmis.LineItem,
						parse: true
					});
				}
				if (resp.payments) {
					var urlRoot = this.url() + '/payment/';
					var paymentCollection = new openhmis.GenericCollection([], { model: openhmis.Payment });
					paymentCollection.add(resp.payments, { parse: true, urlRoot: urlRoot });
					//paymentCollection.reset(paymentCollection.reject(function(payment) { return payment.get("voided"); }));
					resp.payments = paymentCollection;
				}
				return resp;
			}
		});
	}
);